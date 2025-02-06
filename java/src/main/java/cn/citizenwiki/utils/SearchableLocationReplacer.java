package cn.citizenwiki.utils;

import cn.citizenwiki.config.SearchableLocationReplaceConfig;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import cn.citizenwiki.processor.translation.FullTranslationProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
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

    /**
     * 字典序倒序排列的map
     * <地点译文名，地点英文名>
     * 在替换时，为防止“派罗V”被替换成“派罗[Pyro]V[Pyro V]”，所以需要按照字典序倒序先替换“派罗V”，
     * 这样在替换词检索到“派罗”时，判断已替换关键词中是否包含当前关键词，即可避免上述情况
     */
    private final Map<String, String> localtionMap = new TreeMap<>(Collections.reverseOrder());

    private static final String CONFIG_FILE_NAME = "searchable_location_replace_config.yml";

    /**
     * 替换搜索关键词时需要被过滤掉的key
     */
    private final Set<String> ignoreReplaceSearchKeys = new HashSet<>();

    public SearchableLocationReplacer(Map<String, PZTranslation> mergedTranslateMap) {
        //找到所有地点封装到Map
        for (Map.Entry<String, PZTranslation> entry : mergedTranslateMap.entrySet()) {
            String key = entry.getKey();
            //原文和译文相同的情况下不替换
            if (isSearchableKey(key) && entry.getValue().getOriginal().equals(entry.getValue().getTranslation())) {
                localtionMap.put(entry.getValue().getTranslation(), entry.getValue().getOriginal());
            }
        }
        //读取配置文件
        Yaml yaml = new Yaml();
        try (FileInputStream inputStream = new FileInputStream(CONFIG_FILE_NAME)) {
            // 解析YAML文件为Map
            SearchableLocationReplaceConfig config = yaml.loadAs(inputStream, SearchableLocationReplaceConfig.class);
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
    public String replace(String key,String translation) {
        if (key.startsWith("mission_location") && !ignoreReplaceSearchKeys.contains(key)) {
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

    private static final Pattern PYRO = Pattern.compile("^(?i)pyro\\d*(?!.*_desc)(?!.*_add)(?!.*drlct).*");
    private static final Pattern STANTON = Pattern.compile("^(?i)stanton\\d*(?!.*_desc)(?!.*_add).*");
    private static final Pattern UI = Pattern.compile("^(?i)ui_pregame_port_.*_name");
    private static final Pattern RR = Pattern.compile("^(?i)RR_.*_L[0-9]+(?:(?!_desc).)*$");
    //RR_P{N} 如轨道讣闻站
    private static final Pattern RRP = Pattern.compile("^(?i)RR_P\\d+(?:(?!_desc).)*$");
    private static final Pattern DFM = Pattern.compile("^(?i)dfm_crusader_crusader$");
    //派罗远星站规则
    private static final Pattern ASTEROIDCLUSTER_N_BASE_PYRO_ENCOUNTER_REGION_X = Pattern.compile("^AsteroidCluster_\\d+Base_Pyro_Encounter_Region[A-Za-z]+(_\\d{3})$");
    //焰联监控站匹配规则
    private static final Pattern AsteroidBase_P_N_L_N = Pattern.compile("^AsteroidBase_P\\d+_L\\d+$");


    /**
     * 判断key是否为可搜索
     *
     * @param key
     * @return
     */
    public static boolean isSearchableKey(String key) {
        Matcher pyroMc = PYRO.matcher(key);
        Matcher stantonMc = STANTON.matcher(key);
        Matcher uiMc = UI.matcher(key);
        Matcher rRMc = RR.matcher(key);
        Matcher rrpMc = RRP.matcher(key);
        Matcher dfmMc = DFM.matcher(key);
        Matcher abperMc = ASTEROIDCLUSTER_N_BASE_PYRO_ENCOUNTER_REGION_X.matcher(key);
        Matcher aplMc = AsteroidBase_P_N_L_N.matcher(key);
        return pyroMc.matches() || stantonMc.matches() || uiMc.matches() || rRMc.matches() || rrpMc.matches()
                || dfmMc.matches() || abperMc.matches() || aplMc.matches();
    }
}
