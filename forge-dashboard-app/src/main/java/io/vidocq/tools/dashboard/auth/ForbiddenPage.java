package io.vidocq.tools.dashboard.auth;

/** The few static pages of the login: refused, logged out. Every text is escaped. */
public final class ForbiddenPage {

    private ForbiddenPage() {
    }

    /** The 403 page: why, and a link to try again. */
    public static String html(String reason) {
        return page("Accès refusé", "<h1>Accès refusé</h1><p>" + escape(reason) + "</p>"
                + "<p><a href=\"/\">Réessayer</a></p>");
    }

    public static String loggedOut() {
        return page("Déconnecté", "<h1>Déconnecté</h1><p>Votre session du forge-dashboard est fermée.</p>"
                + "<p><a href=\"/\">Se reconnecter</a></p>");
    }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\">"
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
