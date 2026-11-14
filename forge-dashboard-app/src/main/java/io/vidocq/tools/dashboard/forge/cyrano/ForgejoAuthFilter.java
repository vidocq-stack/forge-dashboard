package io.vidocq.tools.dashboard.forge.cyrano;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/** Sends {@code Authorization: token <FORGEJO_TOKEN>} on every call to the forge. The token is never logged. */
@ApplicationScoped
public class ForgejoAuthFilter implements ClientRequestFilter {

    @Inject
    @ConfigProperty(name = "FORGEJO_TOKEN")
    Optional<String> token;

    @Override
    public void filter(ClientRequestContext request) {
        token.map(String::strip).filter(t -> !t.isEmpty())
                .ifPresent(t -> request.getHeaders().putSingle("Authorization", "token " + t));
    }
}
