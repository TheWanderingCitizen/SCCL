package cn.citizenwiki.api.paratranz;

import cn.citizenwiki.model.dto.paratranz.response.PZFile;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        objectMapper.registerModule(new JavaTimeModule());
        om = objectMapper;
    }

}
