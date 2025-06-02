package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.config.GlobalConfig;
import cn.citizenwiki.match.TranslationRuleProcessor;
import cn.citizenwiki.match.rule.ConfigProvider;
import cn.citizenwiki.model.config.TranslationRuleConfigBean;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import cn.citizenwiki.utils.PinYinUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * 拼音汉化处理器
 */
public class PinYinTranslationProcessor extends CommonTranslationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PinYinTranslationProcessor.class);

    /**
     * 汉化规则处理器 - 决定哪些翻译条目需要进行双语处理
     */
    private final TranslationRuleProcessor ruleProcessor;
    private static final String MATCH_RULE_CONFIG_FILE_NAME = "拼音.yaml";

    public PinYinTranslationProcessor() {
        this(MATCH_RULE_CONFIG_FILE_NAME, GlobalConfig.MatcherRulesConfig::getMatcherRule);
    }

    /**
     * 支持自定义配置的构造函数
     *
     * @param configFileName 配置文件路径
     * @param configProvider 配置提供者，用于获取导入的规则文件
     */
    public PinYinTranslationProcessor(String configFileName, ConfigProvider<TranslationRuleConfigBean> configProvider) {
        super(GithubConfig.PINYIN_BRANCH_NAME);
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
    }

    /**
     * 如果是ptu版本则cdn上传到ptu目录，否则上传到full目录
     *
     * @param lastFileVersion 最新版本号
     * @return
     */
    @Override
    protected String getBucketPath(FileVersion lastFileVersion) {
        return S3Config.PINYIN_DIR + "/global.ini";
    }

    @Override
    public void processBw(PZTranslation pzTranslation, BufferedWriter bw) {
        //写入文件
        if (bw != null) {
            String translation = pzTranslation.getTranslation();
            String key = pzTranslation.getKey();
            if (ruleProcessor.isMatch(key, pzTranslation.getOriginal(), translation)) {
                String pinyin = PinYinUtil.getPinyin(translation);
                if (Objects.nonNull(pinyin)) {
                    translation = translation + "[" + PinYinUtil.getPinyin(translation) + "]";
                }
            }

            try {
                bw.write(String.join("=", key, translation));
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
//        if (!FileVersion.Profile.LIVE.equals(GlobalConfig.SW_PROFILE)) {
//            logger.info("推送通道为[{}]，不发布拼音版本", GlobalConfig.SW_PROFILE.name());
//            return false;
//        }
        return true;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public String getProcessorName() {
        return "拼音汉化处理器";
    }

}
