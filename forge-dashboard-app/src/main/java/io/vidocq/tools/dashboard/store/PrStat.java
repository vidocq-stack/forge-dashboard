package io.vidocq.tools.dashboard.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A pull request, for the opened and merged counts per login. The id is {@code org/repo#n}. */
@Entity
@Table(name = "pr_stat")
public class PrStat {

    @Id
    private String id;
    private String org;
    private String repo;
    private Integer number;
    private String authorLogin;
    private Instant createdAt;
    private Instant mergedAt;

    public PrStat() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrg() { return org; }
    public void setOrg(String org) { this.org = org; }
    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }
    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }
    public String getAuthorLogin() { return authorLogin; }
    public void setAuthorLogin(String authorLogin) { this.authorLogin = authorLogin; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getMergedAt() { return mergedAt; }
    public void setMergedAt(Instant mergedAt) { this.mergedAt = mergedAt; }
}
