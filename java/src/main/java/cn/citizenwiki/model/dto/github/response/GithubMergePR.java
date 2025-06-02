package cn.citizenwiki.model.dto.github.response;

public class GithubMergePR {

    private String sha;            // 合并提交的 SHA（提交的唯一标识）
    private boolean merged;        // 是否成功合并
    private String message;        // 合并消息（如："Merge made by the 'rebase' strategy."）

    // 默认构造函数
    public GithubMergePR() {
    }

    // 带参数的构造函数
    public GithubMergePR(String sha, boolean merged, String message) {
        this.sha = sha;
        this.merged = merged;
        this.message = message;
    }

    // Getter 和 Setter 方法
    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public boolean isMerged() {
        return merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    // toString() 方法
    @Override
    public String toString() {
        return "MergePRResponse{" +
                "sha='" + sha + '\'' +
                ", merged=" + merged +
                ", message='" + message + '\'' +
                '}';
    }
}