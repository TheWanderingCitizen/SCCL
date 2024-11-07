package cn.citizenwiki.model.dto.github.request;

/**
 * github api mergeRequest请求体
 */
public class MergeRequest {

    // 合并提交的标题
    private String commitTitle;

    // 合并提交的消息
    private String commitMessage;

    // 合并方法 (merge, squash, rebase)
    private MergeMethod mergeMethod;


    public static enum MergeMethod{
        merge,
        squash,
        rebase
    }

    public MergeRequest(String commitTitle, String commitMessage, MergeMethod mergeMethod) {
        this.commitTitle = commitTitle;
        this.commitMessage = commitMessage;
        this.mergeMethod = mergeMethod;
    }

    public String getCommitTitle() {
        return commitTitle;
    }

    public void setCommitTitle(String commitTitle) {
        this.commitTitle = commitTitle;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public MergeMethod getMergeMethod() {
        return mergeMethod;
    }

    public void setMergeMethod(MergeMethod mergeMethod) {
        this.mergeMethod = mergeMethod;
    }
}
