package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.config.GlobalConfig;
import cn.citizenwiki.match.ParallelMatchRuleProcessor;
import cn.citizenwiki.match.TranslationRuleProcessor;
import cn.citizenwiki.match.rule.ConfigProvider;
import cn.citizenwiki.model.config.MatchRulesConfigBean;
import cn.citizenwiki.model.config.TranslationRuleConfigBean;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * 双语汉化处理器
 */
public class BothTranslationProcessor extends CommonTranslationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BothTranslationProcessor.class);

    /**
     * 汉化规则处理器 - 决定哪些翻译条目需要进行双语处理
     */
    private final TranslationRuleProcessor ruleProcessor;

    /**
     * 格式化规则处理器 - 决定使用中括号拼接还是换行拼接
     */
    private final ParallelMatchRuleProcessor formatProcessor;

    private static final String MATCH_RULE_CONFIG_FILE_NAME = "双语.yaml";
    private static final String JOINED_WITH_BRACKETS_RULE = "joined_with_brackets";

    /**
     * 默认构造函数
     * 使用默认的配置文件路径和全局配置提供者
     */
    public BothTranslationProcessor() {
        this(MATCH_RULE_CONFIG_FILE_NAME, GlobalConfig.MatcherRulesConfig::getMatcherRule);
    }

    /**
     * 支持自定义配置的构造函数
     *
     * @param configFileName 配置文件路径
     * @param configProvider 配置提供者，用于获取导入的规则文件
     */
    public BothTranslationProcessor(String configFileName, ConfigProvider<TranslationRuleConfigBean> configProvider) {
        super(GithubConfig.DUAL_BRANCH_NAME);

        try {
            // 加载主配置文件
            TranslationRuleConfigBean translationRuleConfigBean = configProvider.getConfig(configFileName);

            if (translationRuleConfigBean == null) {
                logger.warn("配置文件 {} 不存在或加载失败，将使用默认空配置", configFileName);
                translationRuleConfigBean = new TranslationRuleConfigBean();
            }

            // 创建规则处理器，使用新的构造方式
            this.ruleProcessor = TranslationRuleProcessor.fromTranslationRuleConfig(
                    translationRuleConfigBean,
                    GlobalConfig.MatcherRulesConfig::getMatchRulesConfig
            );

            // 创建格式化规则处理器
            this.formatProcessor = createFormatProcessor(translationRuleConfigBean);

            logger.info("{}初始化完成", configFileName);
        } catch (Exception e) {
            String msg = String.format("%s初始化失败：%s", configFileName, e.getMessage());
            logger.error(msg, e);
            throw new RuntimeException(msg, e);
        }

    }

    /**
     * 创建格式化规则处理器
     *
     * @param config 翻译匹配配置
     * @return 格式化规则处理器，如果配置中没有对应规则则返回null
     */
    private ParallelMatchRuleProcessor createFormatProcessor(TranslationRuleConfigBean config) {
        if (config.getExt() != null && config.getExt().containsKey(JOINED_WITH_BRACKETS_RULE)) {
            MatchRulesConfigBean formatRule = config.getExt().get(JOINED_WITH_BRACKETS_RULE);
            if (formatRule != null) {
                logger.debug("找到格式化规则：{}", JOINED_WITH_BRACKETS_RULE);
                return new ParallelMatchRuleProcessor(formatRule);
            }
        }
        logger.warn("未找到格式化规则：{}，将对所有匹配项使用换行拼接", JOINED_WITH_BRACKETS_RULE);
        return null;
    }


    @Override
    protected String getBucketPath(FileVersion lastFileVersion) {
        return S3Config.BOTH_DIR + "/global.ini";
    }

    @Override
    public void processBw(PZTranslation pzTranslation, BufferedWriter bw) {
        String key = pzTranslation.getKey();
        String value = pzTranslation.getTranslation();
        if (this.ruleProcessor.isMatch(pzTranslation.getKey(), pzTranslation.getOriginal(), pzTranslation.getTranslation())) {
            // 使用正则表达式
            if (this.ruleProcessor.isExtMatch(JOINED_WITH_BRACKETS_RULE, key)) {
                value = pzTranslation.getOriginal() + " [" + pzTranslation.getTranslation() + "]";
            } else {
                value = pzTranslation.getOriginal() + "\\n" + pzTranslation.getTranslation();
            }
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
//        if (!FileVersion.Profile.LIVE.equals(GlobalConfig.SW_PROFILE)) {
//            logger.info("推送通道为[{}]，不发布双语版本", GlobalConfig.SW_PROFILE.name());
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
        return "双语汉化处理器";
    }
}
