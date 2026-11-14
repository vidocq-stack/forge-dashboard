package io.vidocq.tools.dashboard.store;

import io.vidocq.tools.dashboard.domain.CiState;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** The last known state of an open pull request, read back at start-up. The id is {@code org/repo#n}. */
@Entity
@Table(name = "pull_request_state")
public class PullRequestState {

    @Id
    private String id;
    private String org;
    private String repo;
    private Integer number;
    private String title;
    private String authorLogin;
    private String headRef;
    private String headSha;
    private String htmlUrl;
    private Boolean draft;
    @Enumerated(EnumType.STRING)
    private CiState ciState;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant observedAt;

    public PullRequestState() {
    }

    public static String idOf(String org, String repo, int number) {
        return org + "/" + repo + "#" + number;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrg() { return org; }
    public void setOrg(String org) { this.org = org; }
    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }
    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthorLogin() { return authorLogin; }
    public void setAuthorLogin(String authorLogin) { this.authorLogin = authorLogin; }
    public String getHeadRef() { return headRef; }
    public void setHeadRef(String headRef) { this.headRef = headRef; }
    public String getHeadSha() { return headSha; }
    public void setHeadSha(String headSha) { this.headSha = headSha; }
    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
    public Boolean getDraft() { return draft; }
    public void setDraft(Boolean draft) { this.draft = draft; }
    public CiState getCiState() { return ciState; }
    public void setCiState(CiState ciState) { this.ciState = ciState; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
}
