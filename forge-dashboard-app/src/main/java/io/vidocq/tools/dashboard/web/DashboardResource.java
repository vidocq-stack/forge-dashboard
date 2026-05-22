package io.vidocq.tools.dashboard.web;

import io.vidocq.tools.dashboard.forgejo.ForgejoClient;
import io.vidocq.tools.dashboard.model.BuildStatus;
import io.vidocq.tools.dashboard.model.CommitInfo;
import io.vidocq.tools.dashboard.model.PullRequestInfo;
import io.vidocq.tools.dashboard.model.RepoInfo;
import io.vidocq.tools.dashboard.service.DashboardService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Path("/")
public class DashboardResource {

    private static final DateTimeFormatter ABS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm", Locale.FRENCH)
            .withZone(java.time.ZoneId.systemDefault());

    @Inject
    DashboardService service;

    @Inject
    ForgejoClient forgejo;

    @GET
    @Path("healthz")
    @Produces(MediaType.TEXT_PLAIN)
    public String healthz() {
        return "ok";
    }

    @GET
    @Path("tiles/repos")
    @Produces(MediaType.TEXT_HTML)
    public Response reposTile() {
        var snap = service.getSnapshot();
        var sb = new StringBuilder(16384);
        if (snap.error() != null) {
            sb.append("<p class=\"error\">Erreur partielle : ").append(esc(snap.error())).append("</p>\n");
        }
        if (snap.repos().isEmpty()) {
            sb.append("<p class=\"placeholder\">aucun repo (orgs scannees : ")
              .append(esc(String.join(", ", service.orgs()))).append(")</p>");
        } else {
            sb.append("<table class=\"repos\">\n");
            sb.append("<thead><tr>")
              .append("<th>Projet</th>")
              .append("<th>Specs</th>")
              .append("<th>Dernier commit</th>")
              .append("<th class=\"col-build\" title=\"Status du dernier build sur main\">Main</th>")
              .append("<th class=\"col-build\" title=\"Derniere PR ouverte + status\">PR</th>")
              .append("</tr></thead>\n");
            sb.append("<tbody>\n");
            for (var r : snap.repos()) renderRow(sb, r);
            sb.append("</tbody></table>\n");
        }
        sb.append("<p class=\"meta\">snapshot : ").append(esc(ABS.format(snap.takenAt())))
          .append(" — ").append(snap.repos().size()).append(" repos</p>");
        return Response.ok(sb.toString()).build();
    }

    @GET
    @Path("tiles/runners")
    @Produces(MediaType.TEXT_HTML)
    public Response runnersTile() {
        var runnersUrl = forgejo.baseUrl() + "/-/admin/actions/runners";
        var html = """
                <p class="placeholder">
                  La liste des runners Actions n'est pas exposee par l'API Forgejo publique.
                </p>
                <p>
                  Consulter l'UI admin :
                  <a href="%s" target="_blank" rel="noopener">%s</a>
                </p>
                """.formatted(esc(runnersUrl), esc(runnersUrl));
        return Response.ok(html).build();
    }

    private void renderRow(StringBuilder sb, RepoInfo r) {
        sb.append("<tr").append(r.archived() ? " class=\"archived\"" : "").append(">");

        sb.append("<td class=\"col-name\"><a href=\"").append(esc(r.htmlUrl()))
          .append("\" target=\"_blank\" rel=\"noopener\">")
          .append(esc(r.fullName())).append("</a>");
        if (r.archived()) sb.append(" <span class=\"badge badge-archived\">archive</span>");
        if (r.priv()) sb.append(" <span class=\"badge badge-private\">prive</span>");
        if (r.description() != null && !r.description().isBlank()) {
            sb.append("<div class=\"desc\">").append(esc(r.description())).append("</div>");
        }
        sb.append("</td>");

        sb.append("<td class=\"col-specs\">");
        if (r.specs() == null || r.specs().isEmpty()) {
            sb.append("<span class=\"muted\">—</span>");
        } else {
            for (var s : r.specs()) {
                sb.append("<span class=\"badge badge-spec\">").append(esc(s)).append("</span> ");
            }
        }
        sb.append("</td>");

        sb.append("<td class=\"col-commit\">").append(renderCommit(r.lastCommit())).append("</td>");

        sb.append("<td class=\"col-build\">").append(renderBuild(r.mainBuild(), r.defaultBranch())).append("</td>");

        sb.append("<td class=\"col-build\">").append(renderPr(r.latestPr(), r.openPullRequests())).append("</td>");

        sb.append("</tr>\n");
    }

    private String renderCommit(CommitInfo c) {
        if (c == null) return "<span class=\"muted\">—</span>";
        var sb = new StringBuilder();
        if (c.htmlUrl() != null && !c.htmlUrl().isBlank()) {
            sb.append("<a href=\"").append(esc(c.htmlUrl()))
              .append("\" target=\"_blank\" rel=\"noopener\"><code>")
              .append(esc(c.shortSha())).append("</code></a> ");
        } else {
            sb.append("<code>").append(esc(c.shortSha())).append("</code> ");
        }
        sb.append(esc(truncate(c.message(), 70)));
        sb.append("<div class=\"meta\">")
          .append(esc(c.author())).append(" — ").append(esc(relative(c.timestamp())))
          .append("</div>");
        return sb.toString();
    }

    private String renderBuild(BuildStatus b, String branch) {
        if (b == null || b.state() == BuildStatus.State.NONE) return "<span class=\"muted\">—</span>";
        var sb = new StringBuilder();
        var label = "build-" + b.state().cssClass();
        var icon = b.state().icon();
        var title = (b.summary() != null && !b.summary().isBlank())
                ? b.summary()
                : (branch == null ? "" : branch);
        if (b.htmlUrl() != null && !b.htmlUrl().isBlank()) {
            sb.append("<a class=\"build ").append(label).append("\" href=\"")
              .append(esc(b.htmlUrl())).append("\" target=\"_blank\" rel=\"noopener\" title=\"")
              .append(esc(title)).append("\">").append(icon).append("</a>");
        } else {
            sb.append("<span class=\"build ").append(label).append("\" title=\"")
              .append(esc(title)).append("\">").append(icon).append("</span>");
        }
        if (b.count() > 1) sb.append(" <span class=\"muted\">×").append(b.count()).append("</span>");
        return sb.toString();
    }

    private String renderPr(PullRequestInfo pr, int totalOpen) {
        if (pr == null) {
            if (totalOpen > 0) {
                return "<span class=\"muted\">" + totalOpen + " ouverte(s)</span>";
            }
            return "<span class=\"muted\">—</span>";
        }
        var sb = new StringBuilder();
        // Icone build + lien vers PR
        var icon = pr.build() == null ? "—" : pr.build().state().icon();
        var css = pr.build() == null ? "none" : pr.build().state().cssClass();
        sb.append("<a class=\"build build-").append(css).append("\" href=\"")
          .append(esc(pr.htmlUrl())).append("\" target=\"_blank\" rel=\"noopener\" title=\"#")
          .append(pr.number()).append(" ").append(esc(truncate(pr.title(), 60))).append("\">")
          .append(icon).append("</a>");
        sb.append(" <a class=\"pr-num\" href=\"").append(esc(pr.htmlUrl()))
          .append("\" target=\"_blank\" rel=\"noopener\">#").append(pr.number()).append("</a>");
        if (totalOpen > 1) {
            sb.append(" <span class=\"muted\">/").append(totalOpen).append("</span>");
        }
        return sb.toString();
    }

    private static String relative(Instant t) {
        if (t == null) return "?";
        var d = Duration.between(t, Instant.now());
        if (d.isNegative()) return "a l'instant";
        var s = d.getSeconds();
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "min";
        if (s < 86400) return (s / 3600) + "h";
        return (s / 86400) + "j";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String esc(String s) {
        if (s == null) return "";
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
