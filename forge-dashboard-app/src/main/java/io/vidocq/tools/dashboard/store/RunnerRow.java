package io.vidocq.tools.dashboard.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An Actions runner, under its CodeFloe id. */
@Entity
@Table(name = "runner")
public class RunnerRow {

    @Id
    private Long id;
    private String org;
    private String uuid;
    private String name;
    /** Comma-separated. */
    private String labels;
    private String version;
    private String lastStatus;
    /** Slots of the runner; null uses the capacity observed over 7 days. */
    private Integer capacityOverride;
    private Instant lastSeenAt;

    public RunnerRow() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrg() { return org; }
    public void setOrg(String org) { this.org = org; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLabels() { return labels; }
    public void setLabels(String labels) { this.labels = labels; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
    public Integer getCapacityOverride() { return capacityOverride; }
    public void setCapacityOverride(Integer capacityOverride) { this.capacityOverride = capacityOverride; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
