package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.forge.ForgeNotFoundException;
import io.vidocq.tools.dashboard.forge.ForgeUnavailableException;

import java.util.function.Supplier;

/**
 * A pass over the repositories of an organisation, one read each. A repository CodeFloe no longer has (404/409) is
 * skipped, and so is one it cannot serve now (5xx): the others go on. The pass fails in {@link #end} only when no
 * repository could be read and one was unavailable (CodeFloe is down: the scheduler backs off and the data is marked
 * stale). A rate limit is not caught: it is the token's, the next repository would hit it too.
 */
final class RepoPass {

    private static final System.Logger LOG = System.getLogger(RepoPass.class.getName());

    /** What became of one repository's read. */
    sealed interface Outcome<T> {
        record Read<T>(T value) implements Outcome<T> {
        }

        record Gone<T>() implements Outcome<T> {
        }

        record Unavailable<T>() implements Outcome<T> {
        }
    }

    private final String org;
    private int read;
    private ForgeUnavailableException firstUnavailable;

    RepoPass(String org) {
        this.org = org;
    }

    <T> Outcome<T> read(String repo, Supplier<T> reading) {
        try {
            T value = reading.get();
            read++;
            return new Outcome.Read<>(value);
        } catch (ForgeNotFoundException gone) {
            LOG.log(System.Logger.Level.DEBUG, "Skipping " + org + "/" + repo + ": " + gone.getMessage());
            return new Outcome.Gone<>();
        } catch (ForgeUnavailableException down) {
            LOG.log(System.Logger.Level.WARNING, "Skipping " + org + "/" + repo + " this pass: " + down.getMessage());
            if (firstUnavailable == null) {
                firstUnavailable = down;
            } else {
                firstUnavailable.addSuppressed(down);
            }
            return new Outcome.Unavailable<>();
        }
    }

    /** Fails the pass when nothing could be read because CodeFloe was unavailable. */
    void end() {
        if (read == 0 && firstUnavailable != null) {
            throw firstUnavailable;
        }
    }
}
