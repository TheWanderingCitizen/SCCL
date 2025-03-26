package cn.citizenwiki.api;

import cn.citizenwiki.http.HttpException;
import cn.citizenwiki.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public abstract class BaseApi {

    private static final Logger logger = LoggerFactory.getLogger(BaseApi.class);

    private final HttpClient httpClient;

    public BaseApi() {
        this.httpClient = buildHttpClient();
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::baseClose));
    }

    protected HttpClient buildHttpClient() {
        return HttpClient.newHttpClient();
    }

    protected HttpClient getHttpClient() {
        return this.httpClient;
    }

    /**
     * 发送 HTTP 请求并处理响应，带有重试机制，重试时间为retries*unitTime
     *
     * @param request  HTTP 请求
     * @param retries  最大重试次数
     * @param unitTime 单位重试时间，单位：毫秒
     * @return 响应体（输入流）
     */
    protected InputStream sendRequestWithRetry(HttpRequest request, int retries, int unitTime) throws HttpException {
        for (int attempt = 1; attempt <= retries; attempt++) { // 改为从 1 开始
            try {
                HttpResponse<InputStream> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                // 检查响应状态码
                if (HttpStatus.isSuccessful(response.statusCode())) {
                    return response.body(); // 返回响应体
                } else if (response.statusCode() >= 500 && attempt < retries) {
                    handleRetry(attempt, retries, unitTime);
                } else {
                    handleHttpException(request, response);
                }
            } catch (IOException e) {
                // 仅在 IOException 时重试
                if (attempt < retries) {
                    handleRetry(attempt, retries, unitTime);
                } else {
                    throw new RuntimeException(e); // 如果是最后一次尝试，则抛出异常
                }
            } catch (HttpException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e); // 其他异常不重试，直接抛出
            }
        }
        throw new RuntimeException("经过 " + retries + " 次尝试后，请求失败");
    }

    /**
     * 默认处理http错误的实现，子类可以重写来获取自己想要的信息
     *
     * @param request
     * @param response
     */
    protected void handleHttpException(HttpRequest request, HttpResponse<InputStream> response) throws HttpException {
        String msg;
        try (InputStream is = response.body()) {
            msg = String.format("http status code:[%d], body:[%s]", response.statusCode(), new String(is.readAllBytes()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        throw new HttpException(msg, request, response);
    }

    /**
     * 处理重试逻辑
     *
     * @param attempt  当前尝试次数
     * @param retries  最大重试次数
     * @param unitTime 单位重试时间，单位：毫秒
     * @throws Exception 重试超过次数时抛出异常
     */
    private void handleRetry(int attempt, int retries, int unitTime) {
        int waitTime = attempt * unitTime; // 等待时间递增，单位为毫秒
        logger.error("请求失败，{} 秒后重试... ({}/{})", (waitTime / 1000), attempt, retries);
        try {
            Thread.sleep(waitTime); // 等待递增的时间后重试
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * 销毁资源
     */
    private void baseClose() {
        this.httpClient.close();
        close();
    }

    /**
     * 子类可以选择实现close
     */
    protected void close() {

    }

}
