package cn.citizenwiki.utils;

import cn.citizenwiki.config.GlobalConfig;
import cn.citizenwiki.match.TranslationRuleProcessor;
import cn.citizenwiki.model.config.SearchableLocationReplaceConfigBean;
import cn.citizenwiki.model.config.TranslationRuleConfigBean;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import cn.citizenwiki.processor.translation.FullTranslationProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用于将译文中的地点文本替换为可以用英文搜索的文本（后面加“[英文原文]”）
 * 例：
 * 派罗 V 上空的废墟空间站
 * 替换为
 * 派罗 V[Pyro V] 上空的废墟空间站[Ruin Station]
 */
public class SearchableLocationReplacer {

    private static final Logger logger = LoggerFactory.getLogger(FullTranslationProcessor.class);

    private static final String CONFIG_FILE_NAME = "地点双语任务地点覆盖替换配置.yaml";

    private static final TranslationRuleProcessor RULE_PROCESSOR;

    private static final String MATCH_RULE_CONFIG_FILE_NAME = "地点双语.yaml";
    private static final String MISSION_TEXT_EXT_RULE_NAME = "mission_text";
    static {
        // 加载主配置文件
        TranslationRuleConfigBean translationRuleConfigBean = GlobalConfig.MatcherRulesConfig.getMatcherRule(MATCH_RULE_CONFIG_FILE_NAME);
        // 创建规则处理器，支持imports机制
        RULE_PROCESSOR  = TranslationRuleProcessor.fromTranslationRuleConfig(
                translationRuleConfigBean
                , GlobalConfig.MatcherRulesConfig::getMatchRulesConfig);
    }

    /**
     * 字典序倒序排列的map
     * <地点译文名，地点英文名>
     * 在替换时，为防止“派罗V”被替换成“派罗[Pyro]V[Pyro V]”，所以需要按照字典序倒序先替换“派罗V”，
     * 这样在替换词检索到“派罗”时，判断已替换关键词中是否包含当前关键词，即可避免上述情况
     */
    private final Map<String, String> localtionMap = new TreeMap<>(Collections.reverseOrder());
    /**
     * 替换搜索关键词时需要被过滤掉的key
     */
    private final Set<String> ignoreReplaceSearchKeys = new HashSet<>();
    public SearchableLocationReplacer(Map<String, PZTranslation> mergedTranslateMap) {
        //找到所有地点封装到Map
        for (Map.Entry<String, PZTranslation> entry : mergedTranslateMap.entrySet()) {
            String key = entry.getKey();
            //原文和译文相同的情况下不替换
            if (RULE_PROCESSOR.isMatch(key, entry.getValue().getOriginal(), entry.getValue().getTranslation())
                    && !entry.getValue().getOriginal().equals(entry.getValue().getTranslation())) {
                localtionMap.put(entry.getValue().getTranslation(), entry.getValue().getOriginal());
            }
        }
        //读取配置文件
        try {
            // 解析YAML文件为Map
            SearchableLocationReplaceConfigBean config = YamlUtil.readObjFromYaml(Path.of(CONFIG_FILE_NAME), SearchableLocationReplaceConfigBean.class);
            if (Objects.nonNull(config.getIgnoreKeys())) {
                ignoreReplaceSearchKeys.addAll(config.getIgnoreKeys());
            }
            //配置文件中的映射覆盖原有映射
            if (Objects.nonNull(config.getOverrideMappings())) {
                localtionMap.putAll(config.getOverrideMappings());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    /**
     * 在地点文本后拼接可被搜索的英文文本
     */
    public String replace(String key, String translation) {
        if (RULE_PROCESSOR.isExtMatch(MISSION_TEXT_EXT_RULE_NAME, key)) {
            //将任务地名中的译名替换为译名[原文]，比如“哈哈斯坦顿”→“哈哈斯坦顿[Stanton]”
            Set<String> replacedWords = new HashSet<>();
            for (Map.Entry<String, String> entry : localtionMap.entrySet()) {
                if (translation.contains(entry.getKey()) && !isReplacedWords(replacedWords, entry.getKey())) {
                    translation = translation.replace(entry.getKey(), entry.getKey() + "[" + entry.getValue() + "]");
                    replacedWords.add(entry.getKey());
                }
            }
            logger.debug("key：[{}]中的地点已被替换，替换后译文：[{}]", key, translation);
        }
        return translation;
    }

    /**
     * 当前关键词是否存在于已替换的关键词中，防止出现重复替换
     *
     * @param replacedWords
     * @param key
     * @return
     */
    private boolean isReplacedWords(Set<String> replacedWords, String key) {
        for (String replacedWord : replacedWords) {
            if (replacedWord.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
