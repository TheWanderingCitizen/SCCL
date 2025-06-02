package cn.citizenwiki.match;

import cn.citizenwiki.match.rule.ConfigProvider;
import cn.citizenwiki.match.rule.MatchRules;
import cn.citizenwiki.model.config.MatchRulesConfigBean;
import cn.citizenwiki.model.config.TranslationRuleConfigBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 翻译规则处理器
 *
 * 该类负责处理翻译匹配规则的加载、合并和验证。
 * 专注于提供 isMatch 和 getMatchReason 方法。
 */
public class TranslationRuleProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TranslationRuleProcessor.class);

    /** 键值匹配规则处理器 - 用于匹配翻译条目的 key 字段 */
    private final ParallelMatchRuleProcessor keyProcessor;

    /** 原文匹配规则处理器 - 用于匹配翻译条目的 original 字段 */
    private final ParallelMatchRuleProcessor originalProcessor;

    /** 译文匹配规则处理器 - 用于匹配翻译条目的 translation 字段 */
    private final ParallelMatchRuleProcessor translationProcessor;

    /** 扩展规则处理器映射 - key是ext属性名，value是对应的处理器 */
    private final Map<String, ParallelMatchRuleProcessor> extProcessors;

    private final ConfigProvider<MatchRulesConfigBean> matchRulesConfigProvider;

    /**
     * 构造函数
     *
     * @param keyRules 键值匹配规则
     * @param originalRules 原文匹配规则
     * @param translationRules 译文匹配规则
     * @param matchRulesConfigProvider MatchRulesConfigBean配置提供者，用于导入机制
     */
    public TranslationRuleProcessor(MatchRulesConfigBean keyRules,
                                    MatchRulesConfigBean originalRules,
                                    MatchRulesConfigBean translationRules,
                                    ConfigProvider<MatchRulesConfigBean> matchRulesConfigProvider) {
        this(keyRules, originalRules, translationRules, null, matchRulesConfigProvider);
    }

    /**
     * 完整构造函数
     *
     * @param keyRules 键值匹配规则
     * @param originalRules 原文匹配规则
     * @param translationRules 译文匹配规则
     * @param extRules 扩展规则映射
     * @param matchRulesConfigProvider MatchRulesConfigBean配置提供者，用于导入机制
     */
    public TranslationRuleProcessor(MatchRulesConfigBean keyRules,
                                    MatchRulesConfigBean originalRules,
                                    MatchRulesConfigBean translationRules,
                                    Map<String, MatchRulesConfigBean> extRules,
                                    ConfigProvider<MatchRulesConfigBean> matchRulesConfigProvider) {
        this.matchRulesConfigProvider = matchRulesConfigProvider;

        // 处理导入并创建处理器
        MatchRulesConfigBean mergedKeyRules = mergeWithImports(keyRules);
        MatchRulesConfigBean mergedOriginalRules = mergeWithImports(originalRules);
        MatchRulesConfigBean mergedTranslationRules = mergeWithImports(translationRules);

        this.keyProcessor = new ParallelMatchRuleProcessor(mergedKeyRules);
        this.originalProcessor = new ParallelMatchRuleProcessor(mergedOriginalRules);
        this.translationProcessor = new ParallelMatchRuleProcessor(mergedTranslationRules);

        // 处理扩展规则
        this.extProcessors = createExtProcessors(extRules);
    }

    /**
     * 从 TranslationRuleConfigBean 创建 TranslationRuleProcessor
     * 这是为了保持与现有代码的兼容性
     */
    public static TranslationRuleProcessor fromTranslationRuleConfig(
            TranslationRuleConfigBean config,
            ConfigProvider<MatchRulesConfigBean> matchRulesConfigProvider) {

        if (config == null) {
            logger.warn("TranslationRuleConfigBean 为 null，将使用默认空配置");
            config = new TranslationRuleConfigBean();
        }

        MatchRulesConfigBean keyRules = config.getKey();
        MatchRulesConfigBean originalRules = config.getOriginal();
        MatchRulesConfigBean translationRules = config.getTranslation();
        Map<String, MatchRulesConfigBean> extRules = config.getExt();

        return new TranslationRuleProcessor(keyRules, originalRules, translationRules, extRules, matchRulesConfigProvider);
    }

    /**
     * 创建扩展规则处理器映射
     */
    private Map<String, ParallelMatchRuleProcessor> createExtProcessors(Map<String, MatchRulesConfigBean> extRules) {
        if (extRules == null || extRules.isEmpty()) {
            logger.debug("没有扩展规则需要处理");
            return Collections.emptyMap();
        }

        Map<String, ParallelMatchRuleProcessor> processors = new HashMap<>();

        for (Map.Entry<String, MatchRulesConfigBean> entry : extRules.entrySet()) {
            String extKey = entry.getKey();
            MatchRulesConfigBean extRule = entry.getValue();

            if (extRule == null) {
                logger.warn("扩展规则 '{}' 的配置为 null，跳过处理", extKey);
                continue;
            }

            try {
                // 处理扩展规则的导入
                MatchRulesConfigBean mergedExtRule = mergeWithImports(extRule);
                ParallelMatchRuleProcessor extProcessor = new ParallelMatchRuleProcessor(mergedExtRule);
                processors.put(extKey, extProcessor);
                logger.debug("成功创建扩展规则处理器：{}", extKey);
            } catch (Exception e) {
                logger.error("创建扩展规则处理器 '{}' 失败：{}", extKey, e.getMessage(), e);
                // 继续处理其他扩展规则，不因为一个失败而中断
            }
        }

        logger.info("成功创建 {} 个扩展规则处理器", processors.size());
        return Collections.unmodifiableMap(processors);
    }

    /**
     * 合并导入的规则
     */
    private MatchRulesConfigBean mergeWithImports(MatchRulesConfigBean ruleConfigBean) {
        if (ruleConfigBean == null || ruleConfigBean.getMatchRules() == null) {
            // 创建一个空的但有效的配置，允许所有内容通过
            MatchRulesConfigBean result = new MatchRulesConfigBean();
            MatchRules emptyRules = new MatchRules();
            result.setMatchRules(emptyRules);
            return result;
        }

        // 使用 MatchRules 的导入机制合并规则
        MatchRules mergedRules = ruleConfigBean.getMatchRules().mergeWithImports(
                matchRulesConfigProvider,
                this::extractMatchRulesFromConfigBean
        );

        MatchRulesConfigBean result = new MatchRulesConfigBean();
        result.setMatchRules(mergedRules);
        return result;
    }

    /**
     * 从 MatchRulesConfigBean 中提取 MatchRules
     * 这个方法用于处理导入的配置文件
     */
    private MatchRules extractMatchRulesFromConfigBean(MatchRulesConfigBean configBean) {
        if (configBean == null || configBean.getMatchRules() == null) {
            return new MatchRules();
        }
        return configBean.getMatchRules();
    }

    /**
     * 检查给定的键值、原文及译文是否分别符合各自的匹配规则
     *
     * @param key         要匹配的键值（翻译条目的标识符）
     * @param original    要匹配的原文（英文原文）
     * @param translation 要匹配的译文（中文翻译）
     * @return 如果键值、原文及译文都满足各自的规则，则返回true；否则返回false
     */
    public boolean isMatch(String key, String original, String translation) {
        return keyProcessor.matches(key)
                && originalProcessor.matches(original)
                && translationProcessor.matches(translation);
    }

    /**
     * 获取匹配原因的详细描述
     *
     * @param key         要检查的键值
     * @param original    要检查的原文
     * @param translation 要检查的译文
     * @return 包含详细匹配信息的字符串
     */
    public String getMatchReason(String key, String original, String translation) {
        return String.join(" ",
                "key：", keyProcessor.getMatchReason(key),
                "original：", originalProcessor.getMatchReason(original),
                "translation：", translationProcessor.getMatchReason(translation));
    }

    /**
     * 获取指定扩展规则的处理器
     *
     * @param extKey 扩展规则的键名
     * @return 对应的处理器，如果不存在则返回null
     */
    public ParallelMatchRuleProcessor getExtProcessor(String extKey) {
        return extProcessors.get(extKey);
    }

    /**
     * 检查指定的扩展规则是否匹配给定的输入
     *
     * @param extKey 扩展规则的键名
     * @param input  要检查的输入字符串
     * @return 如果匹配则返回true，如果扩展规则不存在或不匹配则返回false
     */
    public boolean isExtMatch(String extKey, String input) {
        ParallelMatchRuleProcessor processor = extProcessors.get(extKey);
        if (processor == null) {
            logger.debug("扩展规则 '{}' 不存在", extKey);
            return false;
        }
        return processor.matches(input);
    }

    /**
     * 获取指定扩展规则的匹配原因
     *
     * @param extKey 扩展规则的键名
     * @param input  要检查的输入字符串
     * @return 匹配原因，如果扩展规则不存在则返回相应提示
     */
    public String getExtMatchReason(String extKey, String input) {
        ParallelMatchRuleProcessor processor = extProcessors.get(extKey);
        if (processor == null) {
            return "扩展规则 '" + extKey + "' 不存在";
        }
        return processor.getMatchReason(input);
    }

    /**
     * 获取所有可用的扩展规则键名
     *
     * @return 扩展规则键名的集合
     */
    public Set<String> getAvailableExtKeys() {
        return extProcessors.keySet();
    }

    /**
     * 检查是否存在指定的扩展规则
     *
     * @param extKey 扩展规则的键名
     * @return 如果存在则返回true，否则返回false
     */
    public boolean hasExtRule(String extKey) {
        return extProcessors.containsKey(extKey);
    }

    /**
     * 关闭所有处理器并释放资源
     */
    public void close() {
        // 关闭基础处理器
        if (keyProcessor != null) {
            keyProcessor.close();
        }
        if (originalProcessor != null) {
            originalProcessor.close();
        }
        if (translationProcessor != null) {
            translationProcessor.close();
        }

        // 关闭扩展处理器
        extProcessors.values().forEach(processor -> {
            try {
                processor.close();
            } catch (Exception e) {
                logger.warn("关闭扩展处理器时发生异常：{}", e.getMessage());
            }
        });

        logger.debug("TranslationRuleProcessor 已关闭所有资源");
    }
}