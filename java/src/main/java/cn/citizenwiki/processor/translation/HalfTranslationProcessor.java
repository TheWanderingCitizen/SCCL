package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.config.GlobalConfig;
import cn.citizenwiki.match.TranslationRuleProcessor;
import cn.citizenwiki.match.rule.ConfigProvider;
import cn.citizenwiki.model.config.TranslationRuleConfigBean;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * 半汉化处理器
 */
public class HalfTranslationProcessor extends CommonTranslationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HalfTranslationProcessor.class);

    private final TranslationRuleProcessor ruleProcessor;

    private static final String MATCH_RULE_CONFIG_FILE_NAME = "半汉化.yaml";

    /**
     * 默认构造函数
     * 使用默认的配置文件路径和全局配置提供者
     */
    public HalfTranslationProcessor() {
        this(MATCH_RULE_CONFIG_FILE_NAME, GlobalConfig.MatcherRulesConfig::getMatcherRule);
    }

    /**
     * 支持自定义配置的构造函数
     *
     * @param configFileName 配置文件路径
     * @param configProvider 配置提供者，用于获取导入的规则文件
     */
    public HalfTranslationProcessor(String configFileName, ConfigProvider<TranslationRuleConfigBean> configProvider) {
        super(GithubConfig.HALF_BRANCH_NAME);

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
    protected String getBucketPath(FileVersion lastFileVersion) {
        return S3Config.HALF_DIR + "/global.ini";
    }

    @Override
    public void processBw(PZTranslation pzTranslation, BufferedWriter bw) {
        String value = pzTranslation.getTranslation();
        if (ruleProcessor.isMatch(pzTranslation.getKey(), pzTranslation.getOriginal(), pzTranslation.getTranslation())) {
            value = pzTranslation.getOriginal();
        }
        //写入文件
        if (bw != null) {
            try {
                bw.write(pzTranslation.getKey() + "=" + value);
                if (!value.endsWith("\r") && !value.endsWith("\n")) {
                    bw.newLine();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected boolean shouldPublish(FileVersion lastFileVersion) {
//        if (!FileVersion.Profile.LIVE.equals(GlobalConfig.SW_PROFILE)){
//            logger.info("推送通道为[{}]，不发布半汉化版本", GlobalConfig.SW_PROFILE.name());
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
        return "半汉化处理器";
    }
}
