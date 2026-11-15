package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.Formats;
import io.vidocq.tools.dashboard.domain.JobEta;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.JobView;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/** How far along running jobs and builds are, against the usual duration of each job (see {@link JobEta}). */
public final class Progress {

    private Progress() {
    }

    /** What follows a running job's elapsed time: « · 62 % · reste ~3 min »; empty without an estimate. */
    public static String after(Instant startedAt, Duration usual, Instant now, Messages m) {
        return eta(startedAt, usual, now).map(e -> " · " + Formats.eta(e, m)).orElse("");
    }

    /**
     * A build's progress, that of its running job with the most time left: « 62 % · reste ~3 min »; null when none
     * of its jobs has an estimate. The jobs not started yet are unknown: a build of several stages looks shorter.
     */
    public static String ofBuild(Collection<JobView> running, Instant now, Messages m) {
        return JobEta.longest(running.stream().flatMap(j -> eta(j.startedAt(), j.usual(), now).stream()).toList())
                .map(e -> Formats.eta(e, m)).orElse(null);
    }

    private static Optional<JobEta> eta(Instant startedAt, Duration usual, Instant now) {
        return startedAt == null ? Optional.empty() : JobEta.of(Duration.between(startedAt, now), usual);
    }
}
