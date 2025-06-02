package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.config.GlobalConfig;
import cn.citizenwiki.match.TranslationRuleProcessor;
import cn.citizenwiki.match.rule.ConfigProvider;
import cn.citizenwiki.model.config.TranslationRuleConfigBean;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import cn.citizenwiki.utils.SearchableLocationReplacer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;

/**
 * 可搜索汉化处理器
 */
public class SearchableTranslationProcessor extends CommonTranslationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SearchableTranslationProcessor.class);

    private SearchableLocationReplacer searchableLocationReplacer;
    private static final String MATCH_RULE_CONFIG_FILE_NAME = "地点双语.yaml";

    private final TranslationRuleProcessor ruleProcessor;

    /**
     * 默认构造函数
     * 使用默认的配置文件路径和全局配置提供者
     */
    public SearchableTranslationProcessor() {
        this(MATCH_RULE_CONFIG_FILE_NAME, GlobalConfig.MatcherRulesConfig::getMatcherRule);
    }

    /**
     * 支持自定义配置的构造函数
     *
     * @param configFileName 配置文件路径
     * @param configProvider 配置提供者，用于获取导入的规则文件
     */
    public SearchableTranslationProcessor(String configFileName, ConfigProvider<TranslationRuleConfigBean> configProvider) {
        super(GithubConfig.SEARCH_BRANCH_NAME);

        try {
            // 加载主配置文件
            TranslationRuleConfigBean translationRuleConfigBean = configProvider.getConfig(configFileName);

            // 创建规则处理器，支持imports机制
            this.ruleProcessor = TranslationRuleProcessor.fromTranslationRuleConfig(
                    translationRuleConfigBean
                    , GlobalConfig.MatcherRulesConfig::getMatchRulesConfig);

            logger.info("{}初始化完成", MATCH_RULE_CONFIG_FILE_NAME);
        } catch (Exception e) {
            String msg = String.format("%s初始化失败：%s", MATCH_RULE_CONFIG_FILE_NAME, e.getMessage());
            logger.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    @Override
    public void beforeProcess(Map<String, PZTranslation> mergedTranslateMap, FileVersion lastFileVersion) {
        super.beforeProcess(mergedTranslateMap, lastFileVersion);
        searchableLocationReplacer = new SearchableLocationReplacer(mergedTranslateMap);
    }

    /**
     * 如果是ptu版本则cdn上传到ptu目录，否则上传到full目录
     *
     * @param lastFileVersion 最新版本号
     * @return
     */
    @Override
    protected String getBucketPath(FileVersion lastFileVersion) {
        return S3Config.SEARCH_DIR + "/global.ini";
    }

    @Override
    public void processBw(PZTranslation pzTranslation, BufferedWriter bw) {
        //写入文件
        if (bw != null) {
            String translation = pzTranslation.getTranslation();
            if (ruleProcessor.isMatch(pzTranslation.getKey(), pzTranslation.getOriginal(), translation)) {
                translation = translation.replace(translation, translation + "[" + pzTranslation.getOriginal() + "]");
            }
            translation = searchableLocationReplacer.replace(pzTranslation.getKey(), translation);
            try {
                bw.write(pzTranslation.getKey() + "=" + translation);
                if (!translation.endsWith("\r") && !translation.endsWith("\n")) {
                    bw.newLine();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected boolean shouldPublish(FileVersion lastFileVersion) {
        return true;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public String getProcessorName() {
        return "可搜索汉化处理器";
    }

}
