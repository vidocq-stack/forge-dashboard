package io.vidocq.tools.dashboard.sonar;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory Sonar for the tests: a key absent from a map is an unknown project. {@link #failNext} makes the next
 * call of one method throw.
 */
public class FakeSonarApi implements SonarApi {

    public boolean enabled = true;
    /** project key → measures. */
    public final Map<String, SonarMeasures> measures = new HashMap<>();
    /** project key → gate. */
    public final Map<String, GateStatus> gates = new HashMap<>();
    /** "key#pr" → gate. */
    public final Map<String, GateStatus> pullRequestGates = new HashMap<>();
    /** Method name → number of calls. */
    public final Map<String, Integer> calls = new ConcurrentHashMap<>();

    private final Map<String, RuntimeException> failures = new ConcurrentHashMap<>();

    public FakeSonarApi failNext(String method, RuntimeException e) {
        failures.put(method, e);
        return this;
    }

    private boolean enter(String method) {
        calls.merge(method, 1, Integer::sum);
        RuntimeException e = failures.remove(method);
        if (e != null) {
            throw e;
        }
        return enabled;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public Optional<SonarMeasures> measures(String key) {
        return enter("measures") ? Optional.ofNullable(measures.get(key)) : Optional.empty();
    }

    @Override
    public Optional<GateStatus> gate(String key) {
        return enter("gate") ? Optional.ofNullable(gates.get(key)) : Optional.empty();
    }

    @Override
    public Optional<GateStatus> pullRequestGate(String key, int pr) {
        return enter("pullRequestGate") ? Optional.ofNullable(pullRequestGates.get(key + "#" + pr)) : Optional.empty();
    }
}
