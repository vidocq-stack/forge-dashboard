package io.vidocq.tools.dashboard.store;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** The jobs of an organisation waiting and running during one minute: the maximum seen. One per org and minute. */
@Entity
@Table(name = "queue_sample")
public class QueueSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String org;
    private Instant minute;
    private Integer waiting;
    private Integer running;

    public QueueSample() {
    }

    public QueueSample(String org, Instant minute, int waiting, int running) {
        this.org = org;
        this.minute = minute;
        this.waiting = waiting;
        this.running = running;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrg() { return org; }
    public void setOrg(String org) { this.org = org; }
    public Instant getMinute() { return minute; }
    public void setMinute(Instant minute) { this.minute = minute; }
    public Integer getWaiting() { return waiting; }
    public void setWaiting(Integer waiting) { this.waiting = waiting; }
    public Integer getRunning() { return running; }
    public void setRunning(Integer running) { this.running = running; }
}
