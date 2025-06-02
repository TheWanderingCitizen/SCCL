package cn.citizenwiki.utils;

import cn.citizenwiki.model.config.MatchRulesConfigBean;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class YamlUtilTest {

    @Test
    void testReadObjFromYaml_BothTest() throws IOException, URISyntaxException {
        URL resource = getClass().getClassLoader().getResource("match_rules_test.yaml");
        MatchRulesConfigBean matchRulesConfigBean = YamlUtil.readObjFromYaml(Path.of(resource.toURI()), MatchRulesConfigBean.class);
        assertNotNull(matchRulesConfigBean);
    }

}