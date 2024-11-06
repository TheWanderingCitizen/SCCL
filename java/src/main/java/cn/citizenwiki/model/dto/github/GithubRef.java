package cn.citizenwiki.model.dto.github;

public class GithubRef {

    private String label;
    private String ref;
    private String sha;
    private GithubUser user;
    private GithubRepo repo;

    // 生成getter和setter方法
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public GithubUser getUser() {
        return user;
    }

    public void setUser(GithubUser user) {
        this.user = user;
    }

    public GithubRepo getRepo() {
        return repo;
    }

    public void setRepo(GithubRepo repo) {
        this.repo = repo;
    }
}