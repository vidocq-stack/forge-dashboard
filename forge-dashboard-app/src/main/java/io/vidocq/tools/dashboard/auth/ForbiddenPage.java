package io.vidocq.tools.dashboard.auth;

import io.vidocq.tools.dashboard.i18n.Messages;

/**
 * The few static pages of the login: refused, logged out, in the language of the browser (no user yet, or no more).
 * Every text is escaped.
 */
public final class ForbiddenPage {

    private ForbiddenPage() {
    }

    /** The 403 page: why, and a link to try again. */
    public static String html(Messages m, String reason) {
        String title = m.text("auth.forbidden.title");
        return page(m, title, "<h1>" + escape(title) + "</h1><p>" + escape(reason) + "</p>"
                + "<p><a href=\"/\">" + escape(m.text("auth.retry")) + "</a></p>");
    }

    public static String loggedOut(Messages m) {
        String title = m.text("auth.loggedOut.title");
        return page(m, title, "<h1>" + escape(title) + "</h1><p>" + escape(m.text("auth.loggedOut.body")) + "</p>"
                + "<p><a href=\"/\">" + escape(m.text("auth.signInAgain")) + "</a></p>");
    }

    private static String page(Messages m, String title, String body) {
        return "<!doctype html><html lang=\"" + m.locale().getLanguage() + "\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + escape(title) + " — forge-dashboard</title>"
                + "<style>body{font-family:system-ui,sans-serif;max-width:40rem;margin:4rem auto;padding:0 1rem;"
                + "color:#1f2933}a{color:#1d4ed8}</style></head><body>" + body + "</body></html>";
    }

    static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
