package cn.citizenwiki.api.paratranz;

import cn.citizenwiki.api.BaseApi;
import cn.citizenwiki.http.HttpException;
import cn.citizenwiki.model.dto.paratranz.response.PZFile;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;

/**
 * Paratranz API封装
 *
 * @see <a href="https://paratranz.cn/docs">接口文档</a>
 */
public class ParatranzApi extends BaseApi {

    public static final ParatranzApi INSTANCE = new ParatranzApi();

    private ParatranzApi() {
    }

    /**
     * 构建http客户端
     *
     * @return
     */
    protected HttpClient buildHttpClient() {
        final HttpClient httpClient;
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        return httpClient;
    }

    /**
     * 构建auth请求头，锁有接口通用
     *
     * @return
     */
    private HttpRequest.Builder authRequestBuilder() {
        return HttpRequest.newBuilder().header("Authorization", ParatranzConfig.INSTANCE.getToken());
    }

    /**
     * 通用发送请求方法
     *
     * @param request     请求
     * @param typeReference 类型引用
     * @param <T>
     * @return
     */
    private <T> T sendRequestOfJsonResp(HttpRequest request, TypeReference<T> typeReference) {
        try (InputStream respInputStream = sendRequestWithRetry(request, ParatranzConfig.INSTANCE.getRetryNum(), ParatranzConfig.INSTANCE.getUnitTime())) {
            return ParatranzJacksonTools.om.readValue(respInputStream, typeReference);
        } catch (IOException | HttpException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * /projects/{projectId}/files
     */
    public List<PZFile> projectFiles() {
        HttpRequest request = authRequestBuilder()
                .uri(URI.create(ParatranzConfig.INSTANCE.getUrlFiles()))
                .GET()
                .build();
        return sendRequestOfJsonResp(request, ParatranzJacksonTools.LIST_FILE);
    }

    /**
     * /projects/{projectId}/files/{fileId}/translation
     *
     * @param fileId 文件id
     * @return
     */
    public List<PZTranslation> fileTranslation(Long fileId) {
        String urlString = ParatranzConfig.INSTANCE.getUrlFiles() + "/" + fileId + "/translation";
        HttpRequest request = authRequestBuilder()
                .uri(URI.create(urlString))
                .GET()
                .build();
        return sendRequestOfJsonResp(request, ParatranzJacksonTools.LIST_TRANSLATION);
    }


}
