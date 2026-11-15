package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.domain.Formats;
import io.vidocq.tools.dashboard.forge.ForgeLinks;
import io.vidocq.tools.dashboard.store.CommitStat;
import io.vidocq.tools.dashboard.store.CommitStatRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The recent commits of a person (theirs and their agents'), newest first: repo, first line linked to CodeFloe, the
 * agents and the Claude session when the trailers name them.
 */
public class CommitterDetailsDrawer extends Div {

    private final Messages messages = Texts.current();

    static final int MAX = 50;

    private final CommitStatRepository commits;
    private final ForgeLinks links;
    private final Clock clock;

    CommitterDetailsDrawer(CommitStatRepository commits, ForgeLinks links, Clock clock) {
        this.commits = commits;
        this.links = links;
        this.clock = clock;
        Parts.drawer(this);
    }

    /** {@code org} null for all the orgs. */
    void open(String humanKey, String org, int periodDays) {
        removeAll();
        setVisible(true);
        add(Parts.drawerTop(humanKey, this::close));
        Instant now = clock.instant();
        List<CommitStat> recent = commits.findByHumanKeyAndAuthoredAtGreaterThanOrderByAuthoredAtDesc(humanKey,
                        now.minus(Duration.ofDays(periodDays))).stream()
                .filter(c -> org == null || org.equals(c.getOrg()))
                .toList();
        add(Parts.muted(recent.size() + " commits sur " + periodDays + " jours"
                + (org == null ? "" : " dans " + org) + (recent.size() > MAX ? ", les " + MAX + " derniers :" : "")));
        recent.stream().limit(MAX).forEach(c -> add(line(c, now)));
    }

    void close() {
        setVisible(false);
        removeAll();
    }

    private Component line(CommitStat c, Instant now) {
        String title = c.getMessage() == null ? c.getSha() : c.getMessage().lines().findFirst().orElse(c.getSha());
        List<Component> parts = new ArrayList<>(List.of(
                Parts.muted(Formats.relative(c.getAuthoredAt(), now, messages)),
                new Span(c.getRepo()),
                Parts.link(title, links.repo(c.getOrg(), c.getRepo()) + "/commit/" + c.getSha())));
        if (c.getAgentFamilies() != null) {
            parts.add(Parts.muted("avec " + c.getAgentFamilies().replace(",", ", ")));
        }
        if (c.getClaudeSession() != null && c.getClaudeSession().startsWith("https://")) {
            parts.add(Parts.link("session", c.getClaudeSession()));
        }
        Div line = new Div(Parts.line(parts.toArray(Component[]::new)));
        line.getStyle().set("padding", "var(--lumo-space-xs) 0").set("border-bottom", "1px solid var(--lumo-contrast-5pct)");
        return line;
    }
}
