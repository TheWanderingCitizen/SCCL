package cn.citizenwiki.model.dto.github.response;

import java.time.ZonedDateTime;
import java.util.List;

public class GithubPulls {

    private String url;
    private Long id;
    private String nodeId;
    private String htmlUrl;
    private String diffUrl;
    private String patchUrl;
    private String issueUrl;
    private String commitsUrl;
    private String reviewCommentsUrl;
    private String reviewCommentUrl;
    private String commentsUrl;
    private String statusesUrl;
    private Long number;
    private String state;
    private Boolean locked;
    private String title;
    private GithubUser user;
    private String body;
    private List<GithubLabel> labels;
    private GithubMilestone milestone;
    private String activeLockReason;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime closedAt;
    private ZonedDateTime mergedAt;
    private String mergeCommitSha;
    private GithubUser assignee;
    private List<GithubUser> assignees;
    private List<GithubUser> requestedReviewers;
    private List<GithubTeam> requestedTeams;
    private GithubRef head;
    private GithubRef base;
    private GithubLinks _links;
    private String authorAssociation;
    private Object autoMerge;
    private Boolean draft;
    private Boolean merged;
    private Boolean mergeable;
    private Boolean rebaseable;
    private String mergeableState;
    private GithubUser mergedBy;
    private Integer comments;
    private Integer reviewComments;
    private Boolean maintainerCanModify;
    private Integer commits;
    private Integer additions;
    private Integer deletions;
    private Integer changedFiles;

    // 生成getter和setter方法
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public String getDiffUrl() {
        return diffUrl;
    }

    public void setDiffUrl(String diffUrl) {
        this.diffUrl = diffUrl;
    }

    public String getPatchUrl() {
        return patchUrl;
    }

    public void setPatchUrl(String patchUrl) {
        this.patchUrl = patchUrl;
    }

    public String getIssueUrl() {
        return issueUrl;
    }

    public void setIssueUrl(String issueUrl) {
        this.issueUrl = issueUrl;
    }

    public String getCommitsUrl() {
        return commitsUrl;
    }

    public void setCommitsUrl(String commitsUrl) {
        this.commitsUrl = commitsUrl;
    }

    public String getReviewCommentsUrl() {
        return reviewCommentsUrl;
    }

    public void setReviewCommentsUrl(String reviewCommentsUrl) {
        this.reviewCommentsUrl = reviewCommentsUrl;
    }

    public String getReviewCommentUrl() {
        return reviewCommentUrl;
    }

    public void setReviewCommentUrl(String reviewCommentUrl) {
        this.reviewCommentUrl = reviewCommentUrl;
    }

    public String getCommentsUrl() {
        return commentsUrl;
    }

    public void setCommentsUrl(String commentsUrl) {
        this.commentsUrl = commentsUrl;
    }

    public String getStatusesUrl() {
        return statusesUrl;
    }

    public void setStatusesUrl(String statusesUrl) {
        this.statusesUrl = statusesUrl;
    }

    public Long getNumber() {
        return number;
    }

    public void setNumber(Long number) {
        this.number = number;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public GithubUser getUser() {
        return user;
    }

    public void setUser(GithubUser user) {
        this.user = user;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<GithubLabel> getLabels() {
        return labels;
    }

    public void setLabels(List<GithubLabel> labels) {
        this.labels = labels;
    }

    public GithubMilestone getMilestone() {
        return milestone;
    }

    public void setMilestone(GithubMilestone milestone) {
        this.milestone = milestone;
    }

    public String getActiveLockReason() {
        return activeLockReason;
    }

    public void setActiveLockReason(String activeLockReason) {
        this.activeLockReason = activeLockReason;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public ZonedDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(ZonedDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public ZonedDateTime getMergedAt() {
        return mergedAt;
    }

    public void setMergedAt(ZonedDateTime mergedAt) {
        this.mergedAt = mergedAt;
    }

    public String getMergeCommitSha() {
        return mergeCommitSha;
    }

    public void setMergeCommitSha(String mergeCommitSha) {
        this.mergeCommitSha = mergeCommitSha;
    }

    public GithubUser getAssignee() {
        return assignee;
    }

    public void setAssignee(GithubUser assignee) {
        this.assignee = assignee;
    }

    public List<GithubUser> getAssignees() {
        return assignees;
    }

    public void setAssignees(List<GithubUser> assignees) {
        this.assignees = assignees;
    }

    public List<GithubUser> getRequestedReviewers() {
        return requestedReviewers;
    }

    public void setRequestedReviewers(List<GithubUser> requestedReviewers) {
        this.requestedReviewers = requestedReviewers;
    }

    public List<GithubTeam> getRequestedTeams() {
        return requestedTeams;
    }

    public void setRequestedTeams(List<GithubTeam> requestedTeams) {
        this.requestedTeams = requestedTeams;
    }

    public GithubRef getHead() {
        return head;
    }

    public void setHead(GithubRef head) {
        this.head = head;
    }

    public GithubRef getBase() {
        return base;
    }

    public void setBase(GithubRef base) {
        this.base = base;
    }

    public GithubLinks get_links() {
        return _links;
    }

    public void set_links(GithubLinks _links) {
        this._links = _links;
    }

    public String getAuthorAssociation() {
        return authorAssociation;
    }

    public void setAuthorAssociation(String authorAssociation) {
        this.authorAssociation = authorAssociation;
    }

    public Object getAutoMerge() {
        return autoMerge;
    }

    public void setAutoMerge(Object autoMerge) {
        this.autoMerge = autoMerge;
    }

    public Boolean getDraft() {
        return draft;
    }

    public void setDraft(Boolean draft) {
        this.draft = draft;
    }

    public Boolean getMerged() {
        return merged;
    }

    public void setMerged(Boolean merged) {
        this.merged = merged;
    }

    public Boolean getMergeable() {
        return mergeable;
    }

    public void setMergeable(Boolean mergeable) {
        this.mergeable = mergeable;
    }

    public Boolean getRebaseable() {
        return rebaseable;
    }

    public void setRebaseable(Boolean rebaseable) {
        this.rebaseable = rebaseable;
    }

    public String getMergeableState() {
        return mergeableState;
    }

    public void setMergeableState(String mergeableState) {
        this.mergeableState = mergeableState;
    }

    public GithubUser getMergedBy() {
        return mergedBy;
    }

    public void setMergedBy(GithubUser mergedBy) {
        this.mergedBy = mergedBy;
    }

    public Integer getComments() {
        return comments;
    }

    public void setComments(Integer comments) {
        this.comments = comments;
    }

    public Integer getReviewComments() {
        return reviewComments;
    }

    public void setReviewComments(Integer reviewComments) {
        this.reviewComments = reviewComments;
    }

    public Boolean getMaintainerCanModify() {
        return maintainerCanModify;
    }

    public void setMaintainerCanModify(Boolean maintainerCanModify) {
        this.maintainerCanModify = maintainerCanModify;
    }

    public Integer getCommits() {
        return commits;
    }

    public void setCommits(Integer commits) {
        this.commits = commits;
    }

    public Integer getAdditions() {
        return additions;
    }

    public void setAdditions(Integer additions) {
        this.additions = additions;
    }

    public Integer getDeletions() {
        return deletions;
    }

    public void setDeletions(Integer deletions) {
        this.deletions = deletions;
    }

    public Integer getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(Integer changedFiles) {
        this.changedFiles = changedFiles;
    }
}