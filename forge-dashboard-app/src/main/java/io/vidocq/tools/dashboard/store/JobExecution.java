package io.vidocq.tools.dashboard.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One execution of a job by a runner, keyed by the Actions task id. {@code stoppedAt} is null while it runs;
 * {@code runnerId} is null until the head of the log names the runner.
 */
@Entity
@Table(name = "job_execution")
public class JobExecution {

    @Id
    private Long taskId;
    private Long jobId;
    private Long runId;
    private String org;
    private String repo;
    private Long runnerId;
    private Integer prNumber;
    private String jobName;
    private String runTitle;
    private String event;
    private String ref;
    private String commitSha;
    private String status;
    private String htmlUrl;
    private Instant startedAt;
    private Instant stoppedAt;

    public JobExecution() {
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getOrg() { return org; }
    public void setOrg(String org) { this.org = org; }
    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }
    public Long getRunnerId() { return runnerId; }
    public void setRunnerId(Long runnerId) { this.runnerId = runnerId; }
    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public String getRunTitle() { return runTitle; }
    public void setRunTitle(String runTitle) { this.runTitle = runTitle; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getStoppedAt() { return stoppedAt; }
    public void setStoppedAt(Instant stoppedAt) { this.stoppedAt = stoppedAt; }
}
