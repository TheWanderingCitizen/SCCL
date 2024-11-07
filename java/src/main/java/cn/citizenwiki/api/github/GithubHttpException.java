package cn.citizenwiki.api.github;

import cn.citizenwiki.http.HttpException;
import cn.citizenwiki.model.dto.github.response.GitHubErrorResponse;

public class GithubHttpException extends HttpException {

    private GitHubErrorResponse gitHubErrorResponse;

    public GithubHttpException(HttpException e, String msg) {
        super(msg, e.getRequest(), e.getResponse());
        try {
            this.gitHubErrorResponse = GithubJacksonTools.om.readValue(e.getMessage(), GitHubErrorResponse.class);
        }catch (Exception ge){

        }

    }

    public GithubHttpException(HttpException e) {
        this(e, e.getMessage());
    }

    public GitHubErrorResponse getGitHubErrorResponse() {
        return gitHubErrorResponse;
    }
}
