package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;
import io.vidocq.tools.dashboard.auth.DashboardUser;

import java.util.Optional;

/** The CodeFloe user of the current Vaadin request, put in the HTTP session by the login callback. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<DashboardUser> get() {
        VaadinSession vaadin = VaadinSession.getCurrent();
        WrappedSession session = vaadin == null ? null : vaadin.getSession();
        return session != null && session.getAttribute(DashboardUser.ATTR) instanceof DashboardUser user
                ? Optional.of(user)
                : Optional.empty();
    }
}
