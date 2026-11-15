package io.vidocq.tools.dashboard.ui;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;
import io.vidocq.tools.dashboard.auth.DashboardUser;

/**
 * A Vaadin UI in a given language around a test, as Vaadin sets it from the browser, optionally the signed-in user
 * that the login callback puts in the HTTP session, and the browser's {@code Accept-Language} on the current request.
 * Outside of it the views have no UI: English, no user.
 */
final class TestUi {

    private TestUi() {
    }

    static void run(Locale locale, Runnable body) {
        run(locale, null, body);
    }

    static void run(Locale locale, DashboardUser user, Runnable body) {
        get(locale, user, () -> {
            body.run();
            return null;
        });
    }

    static <T> T get(Locale locale, Supplier<T> body) {
        return get(locale, null, body);
    }

    static <T> T get(Locale locale, DashboardUser user, Supplier<T> body) {
        return get(locale, user, null, body);
    }

    /** {@code acceptLanguage}: the header of the request being served; {@code null} for no request. */
    static <T> T get(Locale locale, DashboardUser user, String acceptLanguage, Supplier<T> body) {
        // Vaadin reads the UI's locale (a signal) under the session lock, as in a request
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        VaadinSession session = sessionOf(user, lock);
        VaadinSession.setCurrent(session);
        UI ui = new UI();
        ui.getInternals().setSession(session);
        ui.setLocale(locale);
        UI.setCurrent(ui);
        if (acceptLanguage != null) {
            CurrentInstance.set(VaadinRequest.class, requestWith(acceptLanguage));
        }
        try {
            return body.get();
        } finally {
            CurrentInstance.set(VaadinRequest.class, null);
            UI.setCurrent(null);
            VaadinSession.setCurrent(null);
            lock.unlock();
        }
    }

    /** A session whose HTTP session holds {@code user} ({@code null}: nobody signed in). */
    private static VaadinSession sessionOf(DashboardUser user, Lock lock) {
        WrappedSession http = (WrappedSession) Proxy.newProxyInstance(TestUi.class.getClassLoader(),
                new Class<?>[] {WrappedSession.class},
                (proxy, method, args) -> "getAttribute".equals(method.getName()) && DashboardUser.ATTR.equals(args[0])
                        ? user : defaultOf(method));
        return new VaadinSession(null) {
            @Override
            public WrappedSession getSession() {
                return http;
            }

            @Override
            public Lock getLockInstance() {
                return lock;
            }

            /** Production mode, as deployed; anything else at its default. */
            @Override
            public DeploymentConfiguration getConfiguration() {
                return (DeploymentConfiguration) Proxy.newProxyInstance(TestUi.class.getClassLoader(),
                        new Class<?>[] {DeploymentConfiguration.class},
                        (proxy, method, args) -> "isProductionMode".equals(method.getName()) ? Boolean.TRUE
                                : defaultOf(method));
            }
        };
    }

    private static VaadinRequest requestWith(String acceptLanguage) {
        return (VaadinRequest) Proxy.newProxyInstance(TestUi.class.getClassLoader(), new Class<?>[] {VaadinRequest.class},
                (proxy, method, args) -> "getHeader".equals(method.getName()) && "Accept-Language".equals(args[0])
                        ? acceptLanguage : defaultOf(method));
    }

    /** What a proxied method answers when the test does not care: false, zero or null, never a failed unboxing. */
    private static Object defaultOf(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
