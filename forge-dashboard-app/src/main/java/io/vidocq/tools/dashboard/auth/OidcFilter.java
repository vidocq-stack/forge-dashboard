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
 * login; a Vaadin or XHR request gets a 401 instead, which Vaadin shows as an expired session.
 * <p>
 * Shaped as a Jakarta Security {@code HttpAuthenticationMechanism} ({@link #validateRequest}), to be replaced by
 * {@code @OpenIdAuthenticationMechanismDefinition} once Vidocq has Jakarta Security (vidocq#63).
 */
@WebFilter(filterName = "oidc", urlPatterns = "/*")
public class OidcFilter extends HttpFilter {

    /** Mirror of {@code jakarta.security.enterprise.AuthenticationStatus}. */
    enum AuthenticationStatus { SUCCESS, SEND_CONTINUE, SEND_FAILURE }

    @Inject
    OidcSettings settings;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (validateRequest(request, response) == AuthenticationStatus.SUCCESS) {
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

    /** A Vaadin request ({@code v-r=uidl}, heartbeat, push) or an XHR: a redirect to CodeFloe would break it. */
    private static boolean isBackgroundRequest(HttpServletRequest request) {
        String query = request.getQueryString();
        return (query != null && (query.startsWith("v-r=") || query.contains("&v-r=")))
                || request.getHeader("X-Requested-With") != null;
    }
}
