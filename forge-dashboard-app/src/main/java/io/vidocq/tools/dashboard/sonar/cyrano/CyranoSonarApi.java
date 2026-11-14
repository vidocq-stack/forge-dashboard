package io.vidocq.tools.dashboard.sonar.cyrano;

import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.sonar.SonarApi;
import io.vidocq.tools.dashboard.sonar.SonarMeasures;
import io.vidocq.tools.dashboard.sonar.SonarUnavailableException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** {@link SonarApi} over the Sonar Web API, through Cyrano. Disabled, it makes no call. */
@ApplicationScoped
public class CyranoSonarApi implements SonarApi {

    static final String METRICS = "ncloc,tests,coverage,test_failures,test_errors,bugs,vulnerabilities,code_smells,"
            + "duplicated_lines_density,alert_status";

    @Inject
    @RestClient
    SonarClient sonar;

    @Inject
    @ConfigProperty(name = "SONAR_TOKEN")
    Optional<String> token;

    @Override
    public boolean enabled() {
        return token.map(String::strip).filter(t -> !t.isEmpty()).isPresent();
    }

    @Override
    public Optional<SonarMeasures> measures(String key) {
        return read("measures of " + key, () -> sonar.measures(key, METRICS), SonarJson::toMeasures);
    }

    @Override
    public Optional<GateStatus> gate(String key) {
        return read("gate of " + key, () -> sonar.gate(key), SonarJson::toGate);
    }

    @Override
    public Optional<GateStatus> pullRequestGate(String key, int pr) {
        return read("gate of " + key + " PR " + pr, () -> sonar.pullRequestGate(key, pr), SonarJson::toGate);
    }

    private <T> Optional<T> read(String what, Supplier<Response> request, Function<String, T> map) {
        if (!enabled()) {
            return Optional.empty();
        }
        try (Response response = send(what, request)) {
            int status = response.getStatus();
            if (status == 404) {
                return Optional.empty();
            }
            if (status < 200 || status >= 300) {
                throw new SonarUnavailableException("Sonar answered " + status + " for " + what, null);
            }
            return Optional.of(map.apply(response.readEntity(String.class)));
        }
    }

    private static Response send(String what, Supplier<Response> request) {
        try {
            return request.get();
        } catch (WebApplicationException e) {
            return e.getResponse();
        } catch (RuntimeException e) {
            throw new SonarUnavailableException("Sonar unreachable for " + what, e);
        }
    }
}
