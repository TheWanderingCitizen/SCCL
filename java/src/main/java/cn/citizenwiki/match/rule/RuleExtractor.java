package cn.citizenwiki.match.rule;

/**
 * 规则提取器接口，用于从配置对象中提取 MatchRules
 */
@FunctionalInterface
public interface RuleExtractor<T> {
    /**
     * 从配置对象中提取 MatchRules
     *
     * @param config 配置对象
     * @return 提取的 MatchRules，如果不存在返回null
     */
    MatchRules extractRules(T config);
}
