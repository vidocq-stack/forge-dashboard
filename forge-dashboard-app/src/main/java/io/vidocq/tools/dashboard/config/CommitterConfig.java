package io.vidocq.tools.dashboard.config;

import io.vidocq.tools.dashboard.domain.CommitClassifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Who the committers are, from MicroProfile Config: {@code dashboard.committers.bots}, author names of bot accounts
 * added to {@link CommitClassifier#DEFAULT_BOTS} (CSV), and {@code dashboard.committers.aliases}, {@code name=login}
 * or {@code mail=login} pairs for authors CodeFloe does not link to a login (CSV).
 */
@ApplicationScoped
public class CommitterConfig {

    @Inject
    @ConfigProperty(name = "dashboard.committers.bots")
    Optional<String> bots;

    @Inject
    @ConfigProperty(name = "dashboard.committers.aliases")
    Optional<String> aliases;

    public CommitterConfig() {
    }

    /** For the tests; null is an absent property. */
    public CommitterConfig(String bots, String aliases) {
        this.bots = Optional.ofNullable(bots);
        this.aliases = Optional.ofNullable(aliases);
    }

    public CommitClassifier classifier() {
        Set<String> botNames = new HashSet<>(CommitClassifier.DEFAULT_BOTS);
        for (String bot : csv(bots)) {
            botNames.add(bot);
        }
        Map<String, String> aliasMap = new HashMap<>();
        for (String pair : csv(aliases)) {
            int eq = pair.indexOf('=');
            if (eq <= 0 || eq == pair.length() - 1) {
                continue;
            }
            String key = pair.substring(0, eq).strip();
            String login = pair.substring(eq + 1).strip();
            aliasMap.put(key.contains("@") ? key.toLowerCase(Locale.ROOT) : key, login);
        }
        return new CommitClassifier(botNames, aliasMap);
    }

    private static String[] csv(Optional<String> value) {
        return value.map(String::strip).filter(v -> !v.isEmpty())
                .map(v -> java.util.Arrays.stream(v.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                        .toArray(String[]::new))
                .orElse(new String[0]);
    }
}
