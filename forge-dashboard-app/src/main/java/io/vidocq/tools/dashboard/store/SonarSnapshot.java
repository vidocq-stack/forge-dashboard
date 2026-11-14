package io.vidocq.tools.dashboard.store;

import io.vidocq.tools.dashboard.sonar.GateStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The last Sonar measures of a project (id {@code sonarKey}, measures and gate) or of a pull request (id
 * {@code sonarKey#n}, gate only).
 */
@Entity
@Table(name = "sonar_snapshot")
public class SonarSnapshot {

    @Id
    private String id;
    private String sonarKey;
    private Integer prNumber;
    private String org;
    private String repo;
    private Long ncloc;
    private Long tests;
    private Double coverage;
    private Long testFailures;
    private Long testErrors;
    private Long bugs;
    private Long vulnerabilities;
    private Long codeSmells;
    private Double duplicatedLinesDensity;
    @Enumerated(EnumType.STRING)
    private GateStatus gate;
    private Instant measuredAt;

    public SonarSnapshot() {
    }

    public static String idOf(String sonarKey, Integer prNumber) {
        return prNumber == null ? sonarKey : sonarKey + "#" + prNumber;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSonarKey() { return sonarKey; }
    public void setSonarKey(String sonarKey) { this.sonarKey = sonarKey; }
    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }
    public String getOrg() { return org; }
    public void setOrg(String org) { this.org = org; }
    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }
    public Long getNcloc() { return ncloc; }
    public void setNcloc(Long ncloc) { this.ncloc = ncloc; }
    public Long getTests() { return tests; }
    public void setTests(Long tests) { this.tests = tests; }
    public Double getCoverage() { return coverage; }
    public void setCoverage(Double coverage) { this.coverage = coverage; }
    public Long getTestFailures() { return testFailures; }
    public void setTestFailures(Long testFailures) { this.testFailures = testFailures; }
    public Long getTestErrors() { return testErrors; }
    public void setTestErrors(Long testErrors) { this.testErrors = testErrors; }
    public Long getBugs() { return bugs; }
    public void setBugs(Long bugs) { this.bugs = bugs; }
    public Long getVulnerabilities() { return vulnerabilities; }
    public void setVulnerabilities(Long vulnerabilities) { this.vulnerabilities = vulnerabilities; }
    public Long getCodeSmells() { return codeSmells; }
    public void setCodeSmells(Long codeSmells) { this.codeSmells = codeSmells; }
    public Double getDuplicatedLinesDensity() { return duplicatedLinesDensity; }
    public void setDuplicatedLinesDensity(Double duplicatedLinesDensity) { this.duplicatedLinesDensity = duplicatedLinesDensity; }
    public GateStatus getGate() { return gate; }
    public void setGate(GateStatus gate) { this.gate = gate; }
    public Instant getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(Instant measuredAt) { this.measuredAt = measuredAt; }
}
