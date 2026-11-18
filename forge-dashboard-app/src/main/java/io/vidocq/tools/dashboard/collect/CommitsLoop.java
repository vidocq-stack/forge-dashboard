package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.config.CommitterConfig;
import io.vidocq.tools.dashboard.domain.CommitClassification;
import io.vidocq.tools.dashboard.domain.CommitClassifier;
import io.vidocq.tools.dashboard.domain.CommitKind;
import io.vidocq.tools.dashboard.domain.RawCommit;
import io.vidocq.tools.dashboard.forge.ForgeApi;
import io.vidocq.tools.dashboard.forge.ForgeNotFoundException;
import io.vidocq.tools.dashboard.forge.ForgePull;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.store.CommitStat;
import io.vidocq.tools.dashboard.store.CommitStatRepository;
import io.vidocq.tools.dashboard.store.PrStat;
import io.vidocq.tools.dashboard.store.PrStatRepository;
import io.vidocq.tools.dashboard.store.RepoRow;
import io.vidocq.tools.dashboard.store.RepoRowRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The commits loop (60 min): the new commits of each default branch, classified, and the pull requests updated since.
 * <p>
 * A repository never read is back-filled over 90 days, at most {@value #MAX_BACKFILL_PAGES_PER_TICK} pages per tick
 * and per organisation; the pages left are read at the next ticks. Its cursor ({@code last_commit_at}) is set only
 * once the whole back-fill is read: a restart in the middle starts it again, the commits being upserted.
 */
@ApplicationScoped
public class CommitsLoop {

    static final int MAX_BACKFILL_PAGES_PER_TICK = 20;
    static final Duration BACKFILL = Duration.ofDays(90);
    static final String UNATTRIBUTED_AGENT = "?agent";
    private static final int PAGE_SIZE = 50;

    /** A back-fill in progress: the page to read next, and the newest commit read so far. */
    private record Resume(Instant since, int nextPage, Instant newest) {
    }

    private final ForgeApi forge;
    private final CommitStatRepository commits;
    private final PrStatRepository prs;
    private final RepoRowRepository repos;
    private final CommitterConfig config;
    private final ChangeBus bus;
    private final Clock clock;
    private final Map<Long, Resume> resumes = new HashMap<>();
    /** The organisations whose last tick left a repository unread for lack of budget. */
    private final Set<String> starved = new HashSet<>();
    /** The back-fill pages the current tick may still read (ticks are synchronized). */
    private int budget;

    @Inject
    public CommitsLoop(ForgeApi forge, CommitStatRepository commits, PrStatRepository prs, RepoRowRepository repos,
                       CommitterConfig config, ChangeBus bus, Clock clock) {
        this.forge = forge;
        this.commits = commits;
        this.prs = prs;
        this.repos = repos;
        this.config = config;
        this.bus = bus;
        this.clock = clock;
    }

    public synchronized void tick(String org) {
        Instant now = clock.instant();
        CommitClassifier classifier = config.classifier();
        budget = MAX_BACKFILL_PAGES_PER_TICK;
        int fresh = 0;
        boolean skipped = false;
        RepoPass pass = new RepoPass(org);
        for (RepoRow row : repos.findByOrgOrderByNameAsc(org)) {
            if (Boolean.TRUE.equals(row.getArchived()) || Boolean.TRUE.equals(row.getEmptyRepo())) {
                continue;
            }
            if (row.getLastCommitAt() == null && budget <= 0) {
                skipped = true;
                continue;
            }
            if (pass.read(row.getName(), () -> read(org, row, now, classifier))
                    instanceof RepoPass.Outcome.Read<Integer> read) {
                fresh += read.value();
            }
        }
        pass.end();
        if (skipped) {
            starved.add(org);
        } else {
            starved.remove(org);
        }
        if (fresh > 0) {
            bus.publish(new ChangeEvent.CommitsChanged());
        }
    }

    /**
     * Reads the new commits of one repository, the back-fill pages within the tick's budget.
     *
     * @return the number of commits newer than the cursor and of pull requests stored
     */
    private int read(String org, RepoRow row, Instant now, CommitClassifier classifier) {
        Instant cursor = row.getLastCommitAt();
        boolean backfill = cursor == null;
        Resume r = resumes.getOrDefault(row.getId(), new Resume(backfill ? now.minus(BACKFILL) : cursor, 1, cursor));
        int fresh = 0;
        int page = r.nextPage();
        Instant newest = r.newest();
        boolean complete = false;
        while (!backfill || budget > 0) {
            List<RawCommit> batch = forge.listCommits(org, row.getName(), row.getDefaultBranch(), r.since(), page);
            if (backfill) {
                budget--;
            }
            for (RawCommit c : batch) {
                boolean imported = countedElsewhere(org, row.getName(), c);
                if (!imported) {
                    commits.save(stat(org, row.getName(), c, classifier));
                }
                if (c.authoredAt() != null) {
                    if (!imported && (cursor == null || c.authoredAt().isAfter(cursor))) {
                        fresh++;
                    }
                    if (newest == null || c.authoredAt().isAfter(newest)) {
                        newest = c.authoredAt();
                    }
                }
            }
            if (batch.size() < PAGE_SIZE) {
                complete = true;
                break;
            }
            page++;
        }
        if (!complete) {
            resumes.put(row.getId(), new Resume(r.since(), page, newest));
            return fresh;
        }
        resumes.remove(row.getId());
        fresh += savePulls(org, row.getName(), r.since());
        if (newest == null) {
            newest = r.since();   // nothing in 90 days: the next ticks read from there
        }
        if (!newest.equals(cursor)) {
            row.setLastCommitAt(newest);
            repos.save(row);
        }
        return fresh;
    }

    /** Whether a back-fill is left for the next ticks: the scheduler then runs them sooner than the cadence. */
    public synchronized boolean backfillPending() {
        return !resumes.isEmpty() || !starved.isEmpty();
    }

    /**
     * Whether the same commit is already stored under another id: an import into another repository
     * ({@code git filter-repo}) rewrites the SHAs but keeps author date, mail and message, and must not count twice.
     * Checked before {@link #stat}, which may call the forge.
     */
    private boolean countedElsewhere(String org, String repo, RawCommit c) {
        if (c.authoredAt() == null) {
            return false;
        }
        String id = CommitStat.idOf(org, repo, c.sha());
        for (CommitStat s : commits.findByAuthoredAt(c.authoredAt())) {
            if (!id.equals(s.getId()) && Objects.equals(s.getAuthorEmail(), c.authorEmail())
                    && Objects.equals(s.getMessage(), c.message())) {
                return true;
            }
        }
        return false;
    }

    private CommitStat stat(String org, String repo, RawCommit c, CommitClassifier classifier) {
        CommitClassification k = classifier.classify(c);
        String human = k.humanKey();
        if (k.kind() == CommitKind.AGENT_VIA_BOT) {
            human = forge.pullAuthorForCommit(org, repo, c.sha()).orElse(UNATTRIBUTED_AGENT);
        }
        var s = new CommitStat();
        s.setId(CommitStat.idOf(org, repo, c.sha()));
        s.setSha(c.sha());
        s.setOrg(org);
        s.setRepo(repo);
        s.setAuthoredAt(c.authoredAt());
        s.setAuthorName(c.authorName());
        s.setAuthorEmail(c.authorEmail());
        s.setAuthorLogin(c.authorLogin());
        s.setMessage(c.message());
        s.setAdditions(c.additions());
        s.setDeletions(c.deletions());
        s.setKind(k.kind());
        s.setHumanKey(human);
        s.setAgentFamilies(k.agentFamilies().isEmpty() ? null
                : String.join(",", k.agentFamilies().stream().sorted().toList()));
        s.setClaudeSession(k.claudeSession());
        s.setRawTrailers(k.rawTrailers().isEmpty() ? null : String.join("\n", k.rawTrailers()));
        return s;
    }

    /** @return the number of pull requests stored; none when the repository has them disabled (404) */
    private int savePulls(String org, String repo, Instant since) {
        List<ForgePull> updated;
        try {
            updated = forge.listPullsUpdatedSince(org, repo, since);
        } catch (ForgeNotFoundException disabled) {
            return 0;
        }
        int saved = 0;
        for (ForgePull p : updated) {
            if (p.createdAt() == null) {
                continue;
            }
            var s = new PrStat();
            s.setId(org + "/" + repo + "#" + p.number());
            s.setOrg(org);
            s.setRepo(repo);
            s.setNumber(p.number());
            s.setAuthorLogin(p.authorLogin() == null ? "?" : p.authorLogin());
            s.setCreatedAt(p.createdAt());
            s.setMergedAt(p.mergedAt());
            prs.save(s);
            saved++;
        }
        return saved;
    }
}
