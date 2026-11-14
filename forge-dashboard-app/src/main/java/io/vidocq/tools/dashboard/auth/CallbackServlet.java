package io.vidocq.tools.dashboard.auth;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The return from CodeFloe. A success gets a new session id (no fixation), then goes back where the login started.
 * Without the login of this session, one redirect to {@code /} starts a new one, unless the browser sent no session
 * cookie at all: it does not keep it, and a redirect would loop.
 */
@WebServlet("/oauth2/callback")
public class CallbackServlet extends HttpServlet {

    @Inject
    CallbackHandler handler;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        LoginState saved = session != null && session.getAttribute(LoginState.ATTR) instanceof LoginState l ? l : null;
        Map<String, String> query = new HashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (values.length > 0) {
                query.put(name, values[0]);
            }
        });
        switch (handler.handle(query, saved)) {
            case CallbackResult.Success success -> {
                session.removeAttribute(LoginState.ATTR);
                request.changeSessionId();
                session.setAttribute(DashboardUser.ATTR, success.user());
                redirect(response, request.getContextPath() + success.returnTo());
            }
            case CallbackResult.Denied denied -> {
                if (session != null) {
                    session.removeAttribute(LoginState.ATTR);
                }
                page(response, HttpServletResponse.SC_FORBIDDEN, ForbiddenPage.html(denied.reason()));
            }
            case CallbackResult.Restart _ -> {
                if (request.getRequestedSessionId() == null) {
                    page(response, HttpServletResponse.SC_BAD_REQUEST, ForbiddenPage.html(
                            "Le navigateur n'a pas renvoyé le cookie de session : autorisez les cookies pour ce site,"
                                    + " et passez par son adresse HTTPS."));
                } else {
                    redirect(response, request.getContextPath() + "/");
                }
            }
        }
    }

    /**
     * A relative {@code Location}: behind the TLS proxy, an absolute one built from the request could say
     * {@code http}.
     */
    private static void redirect(HttpServletResponse response, String path) {
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", path);
        response.setHeader("Cache-Control", "no-store");
    }

    static void page(HttpServletResponse response, int status, String html) throws IOException {
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(html);
    }
}
