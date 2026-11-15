package io.vidocq.tools.dashboard.store;

import io.vidocq.tools.dashboard.domain.CommitKind;
import io.vidocq.tools.dashboard.domain.Interval;
import io.vidocq.tools.dashboard.domain.JobKey;
import io.vidocq.tools.dashboard.domain.StatusSpan;
import io.vidocq.tools.dashboard.domain.UsualDurations;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The aggregate queries, in native SQL: Mansart Data repositories have no {@code GROUP BY}. Read-only, except
 * {@link #purge}.
 */
@ApplicationScoped
public class StatsQueries {

    private static final String JOB_INTERVALS = """
            SELECT runner_id, started_at, stopped_at FROM job_execution
             WHERE org = ? AND runner_id IS NOT NULL AND started_at < ? AND (stopped_at IS NULL OR stopped_at > ?)
             ORDER BY runner_id, started_at""";

    /**
     * The last successful runs of each job of an org since a moment, at most {@code ?} per job. The window keeps the
     * query on the (org, stopped_at) index: the runners loop asks every 15 s.
     */
    private static final String LAST_SUCCESSES = """
            SELECT repo, job_name, round(extract(epoch FROM stopped_at - started_at) * 1000) FROM (
                SELECT repo, job_name, started_at, stopped_at,
                       row_number() OVER (PARTITION BY repo, job_name ORDER BY stopped_at DESC) AS recent
                  FROM job_execution
                 WHERE org = ? AND stopped_at > ? AND status = 'success'
                   AND job_name IS NOT NULL) runs
             WHERE recent <= ?""";

    private static final String STATUS_CHANGES = """
            SELECT runner_id, status, changed_at FROM (
                SELECT runner_id, status, changed_at FROM runner_status_change
                 WHERE org = ? AND changed_at >= ? AND changed_at < ?
                UNION ALL
                SELECT runner_id, status, changed_at FROM (
                    SELECT DISTINCT ON (runner_id) runner_id, status, changed_at FROM runner_status_change
                     WHERE org = ? AND changed_at < ?
                     ORDER BY runner_id, changed_at DESC) AS before_window
            ) AS spans
             ORDER BY runner_id, changed_at""";

    private static final String COMMITTERS = """
            SELECT coalesce(human_key, author_login, author_name), kind, agent_families, count(*),
                   coalesce(sum(additions), 0), coalesce(sum(deletions), 0)
              FROM commit_stat
             WHERE authored_at >= ? %s
             GROUP BY 1, kind, agent_families
             ORDER BY count(*) DESC, 1 NULLS LAST, agent_families NULLS FIRST""";

    private static final String PR_COUNTS = """
            SELECT author_login,
                   count(*) FILTER (WHERE created_at >= ?),
                   count(*) FILTER (WHERE merged_at >= ?)
              FROM pr_stat
             WHERE (created_at >= ? OR merged_at >= ?) %s
             GROUP BY author_login
             ORDER BY author_login""";

    /**
     * The {@code @Default} pool is a bean of the pool extension, which the build-time validation of Vauban does not
     * see: it is reached through {@code Instance}, as in {@code vidocq-runtime-mansart-h2-example}.
     */
    private final Instance<DataSource> dataSource;

    @Inject
    public StatsQueries(Instance<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Each job of the org with enough successful runs over the {@link UsualDurations#WINDOW} before {@code now}, and
     * its usual duration (see {@link UsualDurations}).
     */
    public Map<JobKey, Duration> usualDurations(String org, Instant now) {
        Map<JobKey, List<Duration>> runs = new HashMap<>();
        query(LAST_SUCCESSES, List.of(org, at(now.minus(UsualDurations.WINDOW)), UsualDurations.LAST_RUNS), rs -> runs
                .computeIfAbsent(new JobKey(rs.getString(1), rs.getString(2)), k -> new ArrayList<>())
                .add(Duration.ofMillis(rs.getLong(3))));
        Map<JobKey, Duration> usual = new HashMap<>();
        runs.forEach((job, durations) -> UsualDurations.median(durations).ifPresent(d -> usual.put(job, d)));
        return usual;
    }

    /**
     * The jobs of {@code org} with a known runner that overlap {@code [from, to)}, unclipped: the caller clips them to
     * its window. A job still running has a null stop.
     */
    public List<Interval> jobIntervals(String org, Instant from, Instant to) {
        return query(JOB_INTERVALS, List.of(org, at(to), at(from)),
                rs -> new Interval(rs.getLong(1), instant(rs, 2), instant(rs, 3)));
    }

    /**
     * The status changes of the runners of {@code org} in {@code [from, to)}, preceded, for each runner, by its last
     * change before {@code from}: the status it had when the window opened.
     */
    public List<StatusSpan> statusChanges(String org, Instant from, Instant to) {
        return query(STATUS_CHANGES, List.of(org, at(from), at(to), org, at(from)),
                rs -> new StatusSpan(rs.getLong(1), rs.getString(2), instant(rs, 3)));
    }

    /** The commits authored since {@code from}, per human (a bot by its author), kind and set of agents, the busiest first. */
    public List<CommitterAggregate> committerAggregates(Optional<String> org, Instant from) {
        List<Object> params = new ArrayList<>(List.of(at(from)));
        org.ifPresent(params::add);
        return query(COMMITTERS.formatted(org.isPresent() ? "AND org = ?" : ""), params,
                rs -> new CommitterAggregate(rs.getString(1), CommitKind.valueOf(rs.getString(2)), rs.getString(3),
                        rs.getLong(4), rs.getLong(5), rs.getLong(6)));
    }

    /** Per login: the pull requests opened since {@code from}, and those merged since {@code from}. */
    public List<PrCounts> prCounts(Optional<String> org, Instant from) {
        List<Object> params = new ArrayList<>(List.of(at(from), at(from), at(from), at(from)));
        org.ifPresent(params::add);
        return query(PR_COUNTS.formatted(org.isPresent() ? "AND org = ?" : ""), params,
                rs -> new PrCounts(rs.getString(1), rs.getLong(2), rs.getLong(3)));
    }

    /**
     * Deletes the jobs finished and the runner status changes before {@code jobsBefore}, and the queue samples before
     * {@code queueBefore}. Each delete commits on its own: a purge cut short is finished by the next one.
     *
     * @return the number of rows deleted
     */
    public int purge(Instant jobsBefore, Instant queueBefore) {
        return update("DELETE FROM job_execution WHERE stopped_at < ?", at(jobsBefore))
                + update("DELETE FROM runner_status_change WHERE changed_at < ?", at(jobsBefore))
                + update("DELETE FROM queue_sample WHERE minute < ?", at(queueBefore));
    }

    @FunctionalInterface
    private interface Row<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private <T> List<T> query(String sql, List<Object> params, Row<T> row) {
        try (Connection c = dataSource.get().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(row.map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Stats query failed: " + e.getMessage(), e);
        }
    }

    private int update(String sql, Object param) {
        try (Connection c = dataSource.get().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, List.of(param));
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Purge failed: " + e.getMessage(), e);
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    /** The PostgreSQL driver binds timestamptz from an OffsetDateTime, not from an Instant. */
    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
