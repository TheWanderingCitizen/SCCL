package cn.citizenwiki.http;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 在http code非成功或者多次尝试失败仍返回错误时抛出
 */
public class HttpException extends Exception {

    private final HttpRequest request;
    private final HttpResponse<InputStream> response;

    public HttpException(String message, HttpRequest request, HttpResponse<InputStream> response) {
        super(message);
        this.request = request;
        this.response = response;
    }


    public HttpRequest getRequest() {
        return request;
    }

    public HttpResponse<InputStream> getResponse() {
        return response;
    }
}
