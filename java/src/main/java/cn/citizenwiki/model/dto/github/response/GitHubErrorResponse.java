package cn.citizenwiki.model.dto.github.response;

import java.util.List;

public class GitHubErrorResponse {

    private String message;             // 错误消息
    private List<Error> errors;         // 错误详情
    private String documentationUrl;    // GitHub 文档 URL
    private String status;              // HTTP 状态码

    // 默认构造函数
    public GitHubErrorResponse() {
    }

    // 带参数的构造函数
    public GitHubErrorResponse(String message, List<Error> errors, String documentationUrl, String status) {
        this.message = message;
        this.errors = errors;
        this.documentationUrl = documentationUrl;
        this.status = status;
    }

    // Getter 和 Setter 方法
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Error> getErrors() {
        return errors;
    }

    public void setErrors(List<Error> errors) {
        this.errors = errors;
    }

    public String getDocumentationUrl() {
        return documentationUrl;
    }

    public void setDocumentationUrl(String documentationUrl) {
        this.documentationUrl = documentationUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // toString() 方法
    @Override
    public String toString() {
        return "GitHubErrorResponse{" +
                "message='" + message + '\'' +
                ", errors=" + errors +
                ", documentationUrl='" + documentationUrl + '\'' +
                ", status='" + status + '\'' +
                '}';
    }

    // 内部类，封装 errors 数组中的每个错误对象
    public static class Error {
        private String resource;  // 错误资源类型
        private String code;      // 错误代码
        private String message;   // 错误消息

        // 默认构造函数
        public Error() {
        }

        // 带参数的构造函数
        public Error(String resource, String code, String message) {
            this.resource = resource;
            this.code = code;
            this.message = message;
        }

        // Getter 和 Setter 方法
        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
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
            return "Error{" +
                    "resource='" + resource + '\'' +
                    ", code='" + code + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
}
