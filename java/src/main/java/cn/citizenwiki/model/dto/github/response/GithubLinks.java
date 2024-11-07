package cn.citizenwiki.model.dto.github.response;

public class GithubLinks {

    private Link self;
    private Link html;
    private Link issue;
    private Link comments;
    private Link reviewComments;
    private Link reviewComment;
    private Link commits;
    private Link statuses;

    // 生成getter和setter方法
    public Link self() {
        return self;
    }

    public void setSelf(Link self) {
        this.self = self;
    }

    public Link getHtml() {
        return html;
    }

    public void setHtml(Link html) {
        this.html = html;
    }

    public Link getSelf() {
        return self;
    }

    public Link getIssue() {
        return issue;
    }

    public void setIssue(Link issue) {
        this.issue = issue;
    }

    public Link getComments() {
        return comments;
    }

    public void setComments(Link comments) {
        this.comments = comments;
    }

    public Link getReviewComments() {
        return reviewComments;
    }

    public void setReviewComments(Link reviewComments) {
        this.reviewComments = reviewComments;
    }

    public Link getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(Link reviewComment) {
        this.reviewComment = reviewComment;
    }

    public Link getCommits() {
        return commits;
    }

    public void setCommits(Link commits) {
        this.commits = commits;
    }

    public Link getStatuses() {
        return statuses;
    }

    public void setStatuses(Link statuses) {
        this.statuses = statuses;
    }
}