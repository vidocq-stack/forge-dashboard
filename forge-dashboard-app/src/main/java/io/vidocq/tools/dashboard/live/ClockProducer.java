package io.vidocq.tools.dashboard.live;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Clock;

/**
 * The time of the application, in UTC; the tests pass a fixed clock to the constructors instead.
 * <p>
 * {@code @Singleton}, not {@code @ApplicationScoped}: an immutable Clock needs no client proxy, and Vauban cannot
 * define one for a class of the boot class loader (NPE, reported to the VIDOCQ session on 2026-11-14).
 */
@ApplicationScoped
public class ClockProducer {

    @Produces
    @Singleton
    public Clock clock() {
        return Clock.systemUTC();
    }
}
