package cn.citizenwiki.api.github;

import cn.citizenwiki.model.dto.github.GitHubContents;
import cn.citizenwiki.model.dto.github.GithubPulls;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * GITHUB API实体解析器常量
 */
public class GithubJacksonTools {

    public static final ObjectMapper om;
    public static final TypeReference<GithubPulls> PULLS = new TypeReference<>() {};
    public static final TypeReference<GitHubContents> CONTENTS = new TypeReference<>() {};

    static {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        om = objectMapper;
    }

}
