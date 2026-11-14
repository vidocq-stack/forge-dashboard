package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.sonar.SonarMeasures;
import io.vidocq.tools.dashboard.store.SonarSnapshot;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The stored Sonar measures of an organisation, by repository and by pull request, and their rows. */
record SonarRows(Map<String, SonarMeasures> byRepo, Map<String, GateStatus> pullGates) {

    static SonarRows of(List<SonarSnapshot> rows) {
        Map<String, SonarMeasures> byRepo = new HashMap<>();
        Map<String, GateStatus> pullGates = new HashMap<>();
        for (SonarSnapshot s : rows) {
            if (s.getRepo() == null) {
                continue;
            }
            if (s.getPrNumber() == null) {
                byRepo.put(s.getRepo(), new SonarMeasures(s.getNcloc(), s.getTests(), s.getCoverage(),
                        s.getTestFailures(), s.getTestErrors(), s.getBugs(), s.getVulnerabilities(), s.getCodeSmells(),
                        s.getDuplicatedLinesDensity(), s.getGate()));
            } else {
                pullGates.put(s.getRepo() + "#" + s.getPrNumber(), s.getGate());
            }
        }
        return new SonarRows(byRepo, pullGates);
    }

    SonarMeasures measures(String repo) {
        return byRepo.get(repo);
    }

    GateStatus pullGate(String repo, int number) {
        return pullGates.get(repo + "#" + number);
    }

    static SonarSnapshot project(String key, String org, String repo, SonarMeasures m, Instant at) {
        SonarSnapshot s = row(SonarSnapshot.idOf(key, null), key, null, org, repo, m.gate(), at);
        s.setNcloc(m.ncloc());
        s.setTests(m.tests());
        s.setCoverage(m.coverage());
        s.setTestFailures(m.testFailures());
        s.setTestErrors(m.testErrors());
        s.setBugs(m.bugs());
        s.setVulnerabilities(m.vulnerabilities());
        s.setCodeSmells(m.codeSmells());
        s.setDuplicatedLinesDensity(m.duplicatedLinesDensity());
        return s;
    }

    static SonarSnapshot pull(String key, String org, String repo, int number, GateStatus gate, Instant at) {
        return row(SonarSnapshot.idOf(key, number), key, number, org, repo, gate, at);
    }

    private static SonarSnapshot row(String id, String key, Integer number, String org, String repo, GateStatus gate,
                                     Instant at) {
        var s = new SonarSnapshot();
        s.setId(id);
        s.setSonarKey(key);
        s.setPrNumber(number);
        s.setOrg(org);
        s.setRepo(repo);
        s.setGate(gate == null ? GateStatus.NONE : gate);
        s.setMeasuredAt(at);
        return s;
    }
}
