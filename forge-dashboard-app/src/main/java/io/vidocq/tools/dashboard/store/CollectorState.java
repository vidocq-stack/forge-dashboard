package io.vidocq.tools.dashboard.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** The freshness of a collector loop, shown in the footer. */
@Entity
@Table(name = "collector_state")
public class CollectorState {

    @Id
    private String loopName;
    private Instant lastSuccessAt;
    private Instant lastErrorAt;
    private String lastError;

    public CollectorState() {
    }

    public String getLoopName() { return loopName; }
    public void setLoopName(String loopName) { this.loopName = loopName; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public Instant getLastErrorAt() { return lastErrorAt; }
    public void setLastErrorAt(Instant lastErrorAt) { this.lastErrorAt = lastErrorAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
