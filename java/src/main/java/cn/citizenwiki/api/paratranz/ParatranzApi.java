package cn.citizenwiki.api.paratranz;

import cn.citizenwiki.api.BaseApi;
import cn.citizenwiki.http.HttpException;
import cn.citizenwiki.model.dto.paratranz.response.PZFile;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

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
     * @param request       请求
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
     * 直接读取全部状态的词条（包括有译文的未翻译/草稿），不使用文件导出结果。
     *
     * @param fileId 文件id
     * @return
     */
    public List<PZTranslation> fileTranslation(Long fileId) {
        List<PZTranslation> translations = new ArrayList<>();
        for (int page = 1; ; page++) {
            JsonNode response = fileStringsPage(fileId, page);
            JsonNode results = response.get("results");
            if (results == null || !results.isArray() || !response.path("pageCount").canConvertToInt()) {
                throw new IllegalStateException("ParaTranz 词条分页响应格式错误，file=" + fileId);
            }
            if (results.isEmpty() && page < response.path("pageCount").asInt()) {
                throw new IllegalStateException("ParaTranz 词条分页提前返回空页，file=" + fileId);
            }
            try {
                List<PZTranslation> entries = ParatranzJacksonTools.om.readerFor(ParatranzJacksonTools.LIST_TRANSLATION)
                        .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .readValue(results);
                translations.addAll(entries);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (page >= response.path("pageCount").asInt()) {
                return translations;
            }
        }
    }

    JsonNode fileStringsPage(Long fileId, int page) {
        // 不传 stage，保留草稿；mode=4 保留文件数据接口原有的隐藏词条覆盖范围。
        String urlString = ParatranzConfig.INSTANCE.getUrlPrefix() + "/strings?file=" + fileId
                + "&page=" + page + "&pageSize=1000&orderBy=id&mode=4";
        HttpRequest request = authRequestBuilder()
                .uri(URI.create(urlString))
                .GET()
                .build();
        return sendRequestOfJsonResp(request, new TypeReference<JsonNode>() {});
    }


}
