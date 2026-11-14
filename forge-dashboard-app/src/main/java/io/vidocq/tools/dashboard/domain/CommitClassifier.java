package io.vidocq.tools.dashboard.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Attributes a commit to a human, a bot, or agents. Bots are matched on the exact author name; agents on the
 * {@code Co-Authored-By} trailers, whatever their case.
 */
public final class CommitClassifier {

    /** The bot accounts of the Vidocq organisations. */
    public static final Set<String> DEFAULT_BOTS =
            Set.of("vidocq-bot", "Vidocq CI Bot", "Vidocq Bot", "BOT", "bot", "Copilot Agent", "Copilot");

    private static final Pattern TRAILER = Pattern.compile("(?im)^(Co-Authored-By|Claude-Session):\\s*(.+)$");

    private record Family(Pattern pattern, Function<Matcher, String> name) {
    }

    /** Matched against the co-author name, so a version or a parenthesis after the family is ignored. */
    private static final List<Family> FAMILIES = List.of(
            new Family(Pattern.compile("(?i)^claude\\s+(opus|sonnet|fable|haiku)\\b"),
                    m -> "Claude " + capitalise(m.group(1))),
            new Family(Pattern.compile("(?i)^claude\\b"), m -> "Claude"),
            new Family(Pattern.compile("(?i)^(openai\\s+)?codex\\b"), m -> "Codex"),
            new Family(Pattern.compile("(?i)^copilot\\b"), m -> "Copilot"),
            new Family(Pattern.compile("(?i)^mistral\\s+vibe\\b"), m -> "Mistral Vibe"),
            new Family(Pattern.compile("(?i)^gemini\\b"), m -> "Gemini"),
            new Family(Pattern.compile("(?i)^cursor\\b"), m -> "Cursor"));

    private final Set<String> botNames;
    private final Map<String, String> aliases;

    /**
     * @param botNames exact author names of the bot accounts
     * @param aliases  author name or mail → human key, for authors the forge does not link to a login
     */
    public CommitClassifier(Set<String> botNames, Map<String, String> aliases) {
        this.botNames = Set.copyOf(botNames);
        this.aliases = Map.copyOf(aliases);
    }

    /** The agent family of a co-author name, without version nor parenthesis; empty for a person. */
    public static Optional<String> agentFamily(String coAuthorName) {
        if (coAuthorName == null) {
            return Optional.empty();
        }
        String name = coAuthorName.strip();
        for (Family family : FAMILIES) {
            Matcher m = family.pattern().matcher(name);
            if (m.find()) {
                return Optional.of(family.name().apply(m));
            }
        }
        return Optional.empty();
    }

    public CommitClassification classify(RawCommit c) {
        List<String> raw = new ArrayList<>();
        Set<String> families = new LinkedHashSet<>();
        String session = null;
        Matcher m = TRAILER.matcher(c.message() == null ? "" : c.message());
        while (m.find()) {
            String value = m.group(2).strip();
            raw.add(m.group(1) + ": " + value);
            if (m.group(1).equalsIgnoreCase("Claude-Session")) {
                session = value;
            } else {
                int lt = value.indexOf('<');
                agentFamily(lt < 0 ? value : value.substring(0, lt)).ifPresent(families::add);
            }
        }
        boolean bot = c.authorName() != null && botNames.contains(c.authorName());
        CommitKind kind = !bot ? CommitKind.HUMAN : families.isEmpty() ? CommitKind.BOT : CommitKind.AGENT_VIA_BOT;
        return new CommitClassification(kind, bot ? null : humanKey(c), List.copyOf(families), session, raw);
    }

    /** Login, else alias of the name or the mail, else the mail in lower case, else the name. */
    private String humanKey(RawCommit c) {
        if (c.authorLogin() != null && !c.authorLogin().isBlank()) {
            return c.authorLogin();
        }
        if (c.authorName() != null && aliases.containsKey(c.authorName())) {
            return aliases.get(c.authorName());
        }
        if (c.authorEmail() != null && !c.authorEmail().isBlank()) {
            String mail = c.authorEmail().strip().toLowerCase(Locale.ROOT);
            return aliases.getOrDefault(mail, aliases.getOrDefault(c.authorEmail(), mail));
        }
        return c.authorName();
    }

    private static String capitalise(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
