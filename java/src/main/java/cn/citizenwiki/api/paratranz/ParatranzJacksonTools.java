package cn.citizenwiki.api.paratranz;

import cn.citizenwiki.model.dto.paratranz.response.PZFile;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;

/**
 * Paratranz API实体解析器常量
 */
public class ParatranzJacksonTools {

    public static final ObjectMapper om;
    public static final TypeReference<List<PZFile>> LIST_FILE = new TypeReference<>() {
    };
    public static final TypeReference<List<PZTranslation>> LIST_TRANSLATION = new TypeReference<>() {
    };

    static {
        ObjectMapper objectMapper = new ObjectMapper();
        // 禁用时间戳格式（避免输出为时间戳）
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JavaTimeModule javaTimeModule = new JavaTimeModule();
//        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
//        javaTimeModule.addSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        objectMapper.registerModule(javaTimeModule);
        om = objectMapper;
    }

}
