package cn.citizenwiki.model.dto.github.response;

import java.time.ZonedDateTime;

public class GithubMilestone {

    private String url;
    private String htmlUrl;
    private String labelsUrl;
    private Integer id;
    private String nodeId;
    private Integer number;
    private String state;
    private String title;
    private String description;
    private GithubUser creator;
    private Integer openIssues;
    private Integer closedIssues;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime closedAt;
    private ZonedDateTime dueOn;

    // 生成getter和setter方法
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public String getLabelsUrl() {
        return labelsUrl;
    }

    public void setLabelsUrl(String labelsUrl) {
        this.labelsUrl = labelsUrl;
    }
}