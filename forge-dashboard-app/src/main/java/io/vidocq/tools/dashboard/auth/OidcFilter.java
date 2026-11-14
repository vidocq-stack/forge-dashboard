package io.vidocq.tools.dashboard.auth;

import jakarta.inject.Inject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Nothing but {@code /oauth2/*} and {@code /healthz} without a CodeFloe login. A page asked without one starts the
 * login; an XHR or a Vaadin bootstrap gets a 401. A Vaadin message, heartbeat or push poll goes on to Vaadin, which
 * answers "session expired" and makes the page reload, so that the reload starts the login (see {@link #sessionExpiry}).
 * <p>
 * Shaped as a Jakarta Security {@code HttpAuthenticationMechanism} ({@link #validateRequest}), to be replaced by
 * {@code @OpenIdAuthenticationMechanismDefinition} once Vidocq has Jakarta Security (vidocq#63).
 */
// asyncSupported: the filter is in front of /VAADIN/push, whose long-polling requests Atmosphere suspends.
@WebFilter(filterName = "oidc", urlPatterns = "/*", asyncSupported = true)
public class OidcFilter extends HttpFilter {

    /** Mirror of {@code jakarta.security.enterprise.AuthenticationStatus}. */
    enum AuthenticationStatus { SUCCESS, NOT_DONE, SEND_CONTINUE, SEND_FAILURE }

    @Inject
    OidcSettings settings;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        AuthenticationStatus status = validateRequest(request, response);
        if (status == AuthenticationStatus.SUCCESS || status == AuthenticationStatus.NOT_DONE) {
            chain.doFilter(request, response);
        }
    }

    AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/oauth2/") || path.equals("/healthz")) {
            return AuthenticationStatus.SUCCESS;
        }
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(DashboardUser.ATTR) instanceof DashboardUser) {
            return AuthenticationStatus.SUCCESS;
        }
        if (sessionExpiry(request, path)) {
            return AuthenticationStatus.NOT_DONE;
        }
        if (isBackgroundRequest(request)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return AuthenticationStatus.SEND_FAILURE;
        }
        String verifier = Pkce.newVerifier();
        String state = Pkce.randomToken();
        String nonce = Pkce.randomToken();
        String query = request.getQueryString();
        String returnTo = "GET".equals(request.getMethod()) ? path + (query == null ? "" : "?" + query) : "/";
        request.getSession(true).setAttribute(LoginState.ATTR, new LoginState(state, nonce, verifier, returnTo));
        response.sendRedirect(AuthorizationRequest.build(settings, state, nonce, Pkce.challengeS256(verifier)).toString());
        return AuthenticationStatus.SEND_CONTINUE;
    }

    /**
     * A message ({@code v-r=uidl}), heartbeat or push poll of a page whose session is gone (a restart, a session cookie
     * another app on the same host replaced). None of them can open a Vaadin session (only a page load, {@code init}
     * and {@code webcomponent-resync} can), and a session without login holds none: Vaadin can only answer that the
     * session expired, and the page then reloads. A 401 left the page on "Connection lost", reconnecting for ever.
     */
    private static boolean sessionExpiry(HttpServletRequest request, String path) {
        String type = request.getParameter("v-r");
        return "/".equals(path) ? "uidl".equals(type) || "heartbeat".equals(type)
                : "/VAADIN/push".equals(path) && "push".equals(type);
    }

    /** A Vaadin request ({@code v-r=uidl}, heartbeat, push) or an XHR: a redirect to CodeFloe would break it. */
    private static boolean isBackgroundRequest(HttpServletRequest request) {
        String query = request.getQueryString();
        return (query != null && (query.startsWith("v-r=") || query.contains("&v-r=")))
                || request.getHeader("X-Requested-With") != null;
    }
}
