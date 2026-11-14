package io.vidocq.tools.dashboard.sonar.cyrano;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/** Sends {@code Authorization: Basic base64(<SONAR_TOKEN>:)}, Sonar's token scheme. The token is never logged. */
@ApplicationScoped
public class SonarAuthFilter implements ClientRequestFilter {

    @Inject
    @ConfigProperty(name = "SONAR_TOKEN")
    Optional<String> token;

    @Override
    public void filter(ClientRequestContext request) {
        token.map(String::strip).filter(t -> !t.isEmpty()).ifPresent(t -> request.getHeaders().putSingle(
                "Authorization",
                "Basic " + Base64.getEncoder().encodeToString((t + ":").getBytes(StandardCharsets.UTF_8))));
    }
}
