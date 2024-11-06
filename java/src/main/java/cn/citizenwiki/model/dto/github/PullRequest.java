package cn.citizenwiki.model.dto.github;

/**
 * github api PullRequest请求体
 */
public class PullRequest {

    private String title;
    private String head;
    private String base;
    private String body;

    public PullRequest(String title, String head, String base, String body) {
        this.title = title;
        this.head = head;
        this.base = base;
        this.body = body;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHead() {
        return head;
    }

    public void setHead(String head) {
        this.head = head;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    // Getter 和 Setter 方法（可以省略，如果不需要 Jackson 自动调用）
}
