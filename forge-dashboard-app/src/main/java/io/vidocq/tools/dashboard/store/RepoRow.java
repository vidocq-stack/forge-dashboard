package io.vidocq.tools.dashboard.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A repository of a tracked organisation, under its CodeFloe id. */
@Entity
@Table(name = "repo")
public class RepoRow {

    @Id
    private Long id;
    private String org;
    private String name;
    private String defaultBranch;
    private Boolean archived;
    private Boolean emptyRepo;
    /** {@code none} disables Sonar; null applies the naming rule of {@code SonarKeys}. */
    private String sonarKeyOverride;
    /** Cursor of the commits loop: the committer date of the newest commit read. */
    private Instant lastCommitAt;

    public RepoRow() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrg() { return org; }
    public void setOrg(String org) { this.org = org; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
    public Boolean getEmptyRepo() { return emptyRepo; }
    public void setEmptyRepo(Boolean emptyRepo) { this.emptyRepo = emptyRepo; }
    public String getSonarKeyOverride() { return sonarKeyOverride; }
    public void setSonarKeyOverride(String sonarKeyOverride) { this.sonarKeyOverride = sonarKeyOverride; }
    public Instant getLastCommitAt() { return lastCommitAt; }
    public void setLastCommitAt(Instant lastCommitAt) { this.lastCommitAt = lastCommitAt; }
}
