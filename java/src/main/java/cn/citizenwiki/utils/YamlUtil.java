package cn.citizenwiki.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class YamlUtil {

    // 创建YAML ObjectMapper
    private static final ObjectMapper yamlMapper;

    static {
        // 配置使用蛇形命名策略（下划线）
        ObjectMapper ym = new ObjectMapper(new YAMLFactory());
        ym.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        ym.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        yamlMapper = ym;

    }

    public static <T> T readObjFromYaml(Path path, Class<T> clazz) throws IOException {
        try(InputStream inputStream = Files.newInputStream(path)) {
            return yamlMapper.readValue(inputStream, clazz);
        }
    }
}
