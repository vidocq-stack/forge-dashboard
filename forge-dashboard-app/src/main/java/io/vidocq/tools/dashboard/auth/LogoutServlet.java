package io.vidocq.tools.dashboard.auth;

import io.vidocq.tools.dashboard.i18n.Messages;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/** Ends the dashboard session only: the CodeFloe session stays (no single logout in v1). */
@WebServlet("/oauth2/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        // no more user: the browser's language
        CallbackServlet.page(response, HttpServletResponse.SC_OK,
                ForbiddenPage.loggedOut(Messages.forAcceptLanguage(request.getHeader("Accept-Language"))));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGet(request, response);
    }
}
