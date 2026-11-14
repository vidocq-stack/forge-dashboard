package io.vidocq.tools.dashboard.auth;

import io.vidocq.cervantes.api.JwtValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The OIDC client from MicroProfile Config: {@code OIDC_CLIENT_ID}, {@code OIDC_CLIENT_SECRET},
 * {@code DASHBOARD_PUBLIC_URL} (required, the application does not start without them), {@code
 * DASHBOARD_ALLOWED_GROUPS} and {@code DASHBOARD_ADMIN_GROUPS}. The issuer is the one Cervantes checks.
 */
@ApplicationScoped
public class AuthBeans {

    @Inject
    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @Inject
    @ConfigProperty(name = "OIDC_CLIENT_ID")
    String clientId;

    @Inject
    @ConfigProperty(name = "OIDC_CLIENT_SECRET")
    String clientSecret;

    @Inject
    @ConfigProperty(name = "DASHBOARD_PUBLIC_URL")
    String publicUrl;

    @Inject
    @ConfigProperty(name = "DASHBOARD_ALLOWED_GROUPS", defaultValue = "Vidocq:owners,Vidocq:devs")
    String allowedGroups;

    @Inject
    @ConfigProperty(name = "DASHBOARD_ADMIN_GROUPS", defaultValue = "Vidocq:owners")
    String adminGroups;

    @Inject
    Instance<JwtValidator> validator;

    @Produces
    @Singleton
    OidcSettings settings() {
        return new OidcSettings(URI.create(issuer.strip()), clientId.strip(), clientSecret.strip(),
                URI.create(publicUrl.strip()), csv(allowedGroups), csv(adminGroups));
    }

    @Produces
    @Singleton
    CallbackHandler callbackHandler() {
        OidcSettings s = settings();
        return new CallbackHandler(s, new HttpTokenEndpoint(s), new CervantesIdTokenVerifier(validator.get()));
    }

    static Set<String> csv(String value) {
        return Arrays.stream(value.split(",")).map(String::strip).filter(g -> !g.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
