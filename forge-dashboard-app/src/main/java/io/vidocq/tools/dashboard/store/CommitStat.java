package io.vidocq.tools.dashboard.store;

import io.vidocq.tools.dashboard.domain.CommitKind;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A commit of a default branch with its classification. The id is {@code org/repo@sha}. */
@Entity
@Table(name = "commit_stat")
public class CommitStat {

    @Id
    private String id;
    private String sha;
    private String org;
    private String repo;
    private Instant authoredAt;
    private String authorName;
    private String authorEmail;
    private String authorLogin;
    private String message;
    private Integer additions;
    private Integer deletions;
    @Enumerated(EnumType.STRING)
    private CommitKind kind;
    /** The resolved human; null for a bot commit. */
    private String humanKey;
    /** The agent families, comma-separated and sorted; null without agent. */
    private String agentFamilies;
    private String claudeSession;
    private String rawTrailers;

    public CommitStat() {
    }

    public static String idOf(String org, String repo, String sha) {
        return org + "/" + repo + "@" + sha;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSha() { return sha; }
    public void setSha(String sha) { this.sha = sha; }
    public String getOrg() { return org; }
    public void setOrg(String org) { this.org = org; }
    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }
    public Instant getAuthoredAt() { return authoredAt; }
    public void setAuthoredAt(Instant authoredAt) { this.authoredAt = authoredAt; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getAuthorEmail() { return authorEmail; }
    public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }
    public String getAuthorLogin() { return authorLogin; }
    public void setAuthorLogin(String authorLogin) { this.authorLogin = authorLogin; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getAdditions() { return additions; }
    public void setAdditions(Integer additions) { this.additions = additions; }
    public Integer getDeletions() { return deletions; }
    public void setDeletions(Integer deletions) { this.deletions = deletions; }
    public CommitKind getKind() { return kind; }
    public void setKind(CommitKind kind) { this.kind = kind; }
    public String getHumanKey() { return humanKey; }
    public void setHumanKey(String humanKey) { this.humanKey = humanKey; }
    public String getAgentFamilies() { return agentFamilies; }
    public void setAgentFamilies(String agentFamilies) { this.agentFamilies = agentFamilies; }
    public String getClaudeSession() { return claudeSession; }
    public void setClaudeSession(String claudeSession) { this.claudeSession = claudeSession; }
    public String getRawTrailers() { return rawTrailers; }
    public void setRawTrailers(String rawTrailers) { this.rawTrailers = rawTrailers; }
}
