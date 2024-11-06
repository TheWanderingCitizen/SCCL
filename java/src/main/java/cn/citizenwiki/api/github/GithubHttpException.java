package cn.citizenwiki.api.github;

import cn.citizenwiki.http.HttpException;

public class GithubHttpException extends HttpException {

    public GithubHttpException(HttpException e, String msg) {
        super(msg, e.getRequest(), e.getResponse());
    }

    public GithubHttpException(HttpException e) {
        super(e.getMessage(), e.getRequest(), e.getResponse());
    }
}
