package cn.citizenwiki.api.github;

import cn.citizenwiki.api.BaseApi;
import cn.citizenwiki.http.HttpException;
import cn.citizenwiki.http.HttpStatus;
import cn.citizenwiki.model.dto.github.GitHubContents;
import cn.citizenwiki.model.dto.github.GithubPulls;
import cn.citizenwiki.model.dto.github.PullRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * github api封装
 *
 * @see <a href="https://docs.github.com/zh/rest">文档</a>
 */
public class GithubApi extends BaseApi {

    private static final Logger logger = LoggerFactory.getLogger(GithubApi.class);

    private final GithubConfig config;

    public static final GithubApi INSTANCE = new GithubApi();

    private GithubApi() {
        this.config = GithubConfig.INSTANCE;
    }


    private static GithubHttpException handleGithubHttpException(HttpException e) {
        String msg = e.getMessage();
        if (HttpStatus.FORBIDDEN.getCode() == e.getResponse().statusCode()) {
            List<String> permissions = e.getResponse().headers().map().get(GithubHeaderConstants.ACCEPTED_PERMISSIONS);
            if (permissions != null && !permissions.isEmpty()) {
                msg += " " + GithubHeaderConstants.ACCEPTED_PERMISSIONS + ":" + String.join(",", permissions);
            }
        }
        return new GithubHttpException(e, msg);
    }

    /**
     * 创建一个新的 Pull Request
     *
     * @param title      Pull Request 的标题
     * @param branchName 源仓库的目标分支（如 "main" 或 "master"）
     * @param body       Pull Request 的正文内容
     * @return 创建的 Pull Request 的详细信息
     * @see <a href="https://docs.github.com/zh/rest/pulls/pulls?apiVersion=2022-11-28#create-a-pull-request">文档</a>
     */
    public GithubPulls createPullRequest(String title, String branchName, String body) {
        String url = String.format("%s/repos/%s/%s/pulls", GithubConfig.BASE_API_URL, config.getTargetUsername(), config.getTargetRepo());

        // 使用 Jackson 库构建 JSON 请求体
        PullRequest pullRequest = new PullRequest(title, config.getForkOwner() + ":" + branchName, branchName, body);
        String jsonBody;
        try {
            jsonBody = GithubJacksonTools.om.writeValueAsString(pullRequest);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        HttpRequest request = authJsonRequestBuilder()
                .uri(URI.create(url))
                .POST(BodyPublishers.ofString(jsonBody))
                .build();
        return sendRequestOfJsonResp(request, GithubJacksonTools.PULLS);
    }

    public GitHubContents getContent(String userName, String repo, String branchName, String contentPath) {
        String url = String.format("%s/repos/%s/%s/contents/%s?ref=%s", GithubConfig.BASE_API_URL, userName, repo, contentPath, branchName);
        HttpRequest request = authJsonRequestBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return sendRequestOfJsonResp(request, GithubJacksonTools.CONTENTS);
    }

    public InputStream downloadContent(String userName, String repo, String branchName, String contentPath) {
        GitHubContents content = getContent(userName, repo, branchName, contentPath);
        HttpRequest request = authJsonRequestBuilder()
                .uri(URI.create(content.getDownloadUrl()))
                .GET()
                .build();
        return sendRequest(request);
    }

    protected HttpClient buildHttpClient() {
        final HttpClient httpClient;
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        return httpClient;
    }

    private HttpRequest.Builder authRequestBuilder() {
        return HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + config.getToken());
    }

    private HttpRequest.Builder authJsonRequestBuilder() {
        return authRequestBuilder()
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json");
    }

    /**
     * 通用发送请求方法
     *
     * @param request 请求
     * @return 响应流
     */
    private InputStream sendRequest(HttpRequest request) {
        try {
            return sendRequestWithRetry(request, GithubConfig.INSTANCE.getRetryNum(), GithubConfig.INSTANCE.getUnitTime());
        } catch (HttpException e) {
            throw new RuntimeException(handleGithubHttpException(e));
        }
    }

    /**
     * 通用发送请求方法
     *
     * @param request       请求
     * @param typeReference 类型引用
     * @param <T> json结构对应的实体
     * @return java bean
     */
    private <T> T sendRequestOfJsonResp(HttpRequest request, TypeReference<T> typeReference) {
        String json = null;
        try (InputStream respInputStream = sendRequestWithRetry(request, GithubConfig.INSTANCE.getRetryNum(), GithubConfig.INSTANCE.getUnitTime())) {
            json = new String(respInputStream.readAllBytes(), StandardCharsets.UTF_8);
            return GithubJacksonTools.om.readValue(json, typeReference);
        } catch (JsonMappingException e) {
            //json解析异常，打印响应内容
            logger.info("{}对应返回json解析异常，json：{}", typeReference.getType().toString(), json);
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (HttpException e) {
            throw new RuntimeException(handleGithubHttpException(e));
        }
    }


}
