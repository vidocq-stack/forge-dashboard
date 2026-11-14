package io.vidocq.tools.dashboard.store;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A runner entering a status (idle, active, offline). */
@Entity
@Table(name = "runner_status_change")
public class RunnerStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long runnerId;
    private String org;
    private String status;
    private Instant changedAt;

    public RunnerStatusChange() {
    }

    public RunnerStatusChange(long runnerId, String org, String status, Instant changedAt) {
        this.runnerId = runnerId;
        this.org = org;
        this.status = status;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunnerId() { return runnerId; }
    public void setRunnerId(Long runnerId) { this.runnerId = runnerId; }
    public String getOrg() { return org; }
    public void setOrg(String org) { this.org = org; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
}
