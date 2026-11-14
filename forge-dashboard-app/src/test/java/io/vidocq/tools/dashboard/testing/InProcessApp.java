package io.vidocq.tools.dashboard.testing;

import io.vidocq.runtime.core.VidocqBootstrap;
import jakarta.enterprise.inject.spi.CDI;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.sql.SQLException;

/**
 * The application started once in the test JVM, on a free port, the way {@code TransactionalRollbackTest} of
 * {@code vidocq-runtime-mansart-h2-example} does. The PostgreSQL of the dev services JUnit listener is already up and
 * its coordinates are system properties. Never stopped: Surefire exits the JVM after the last test class.
 * <p>
 * A bean declared in {@code src/test/java} is never part of the application: the fakes are passed to the
 * constructors of the beans under test instead.
 */
public final class InProcessApp {

    private static VidocqBootstrap bootstrap;

    private InProcessApp() {
    }

    public static synchronized void start() {
        if (bootstrap != null) {
            return;
        }
        System.setProperty("vidocq.launch.mode", "test");
        System.setProperty("vidocq.chappe.listener.default.port", String.valueOf(freePort()));
        System.setProperty("vaadin.productionMode", "true");
        openToRuntime();
        bootstrap = VidocqBootstrap.create().configure().start();
    }

    /**
     * Started this way, the application is in the boot layer, not in the layer of the Vidocq launcher, which exports
     * and opens its packages to the runtime and promotes its generated providers ({@code _VaubanComponents},
     * {@code $$FoyComponent}). The tests are patched into the application module, so they may do it themselves:
     * every package is opened to the Vidocq modules. The examples of Vidocq open their packages in their
     * {@code module-info.java} instead; this keeps the production module closed.
     */
    private static void openToRuntime() {
        Module app = InProcessApp.class.getModule();
        if (!app.isNamed()) {
            return;
        }
        var runtime = ModuleLayer.boot().modules().stream()
                .filter(m -> m.getName().startsWith("io.vidocq.") && m != app)
                .toList();
        for (String pkg : app.getPackages()) {
            for (Module m : runtime) {
                app.addOpens(pkg, m);
            }
        }
    }

    public static <T> T bean(Class<T> type) {
        start();
        return CDI.current().select(type).get();
    }

    /** Empties the tables, identities included, so that each test starts from a known state. */
    public static void truncate(String... tables) {
        try (var c = bean(DataSource.class).getConnection(); var s = c.createStatement()) {
            s.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int freePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
