package cn.citizenwiki.match;

import cn.citizenwiki.match.rule.MatchRules;
import cn.citizenwiki.match.rule.RuleMatcherGroupProcessor;
import cn.citizenwiki.model.config.MatchRulesConfigBean;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * 根据匹配规则配置进行字符串匹配的处理器
 * 使用Fork/Join框架并行处理提高匹配效率
 */
public class ParallelMatchRuleProcessor {

    private final RuleMatcherGroupProcessor includeProcessor;
    private final RuleMatcherGroupProcessor excludeProcessor;
    private final ForkJoinPool forkJoinPool;
    private final boolean isPoolOwner;

    /**
     * 初始化匹配处理器，使用指定的ForkJoinPool
     *
     * @param config       匹配规则配置
     * @param forkJoinPool 要使用的ForkJoinPool
     * @param isPoolOwner  是否拥有池的所有权（负责关闭）
     */
    public ParallelMatchRuleProcessor(MatchRulesConfigBean config, ForkJoinPool forkJoinPool, boolean isPoolOwner) {
        if (config == null || config.getMatchRules() == null) {
            throw new IllegalArgumentException("配置不能为空");
        }

        if (forkJoinPool == null) {
            throw new IllegalArgumentException("ForkJoinPool不能为空");
        }

        this.forkJoinPool = forkJoinPool;
        this.isPoolOwner = isPoolOwner;

        MatchRules rules = config.getMatchRules();

        // 初始化包含和排除规则处理器
        includeProcessor = new RuleMatcherGroupProcessor(rules.getInclude(), forkJoinPool);
        excludeProcessor = new RuleMatcherGroupProcessor(rules.getExclude(), forkJoinPool);
    }

    /**
     * 初始化匹配处理器，使用自定义ForkJoinPool（该处理器拥有池的所有权）
     *
     * @param config      匹配规则配置
     * @param parallelism 并行度
     */
    public ParallelMatchRuleProcessor(MatchRulesConfigBean config, int parallelism) {
        this(config, new ForkJoinPool(parallelism), true);
    }

    /**
     * 初始化匹配处理器，使用共享的ForkJoinPool（该处理器不拥有池的所有权）
     *
     * @param config     匹配规则配置
     * @param sharedPool 共享的ForkJoinPool
     */
    public ParallelMatchRuleProcessor(MatchRulesConfigBean config, ForkJoinPool sharedPool) {
        this(config, sharedPool, false);
    }

    /**
     * 初始化匹配处理器，使用公共ForkJoinPool
     *
     * @param config 匹配规则配置
     */
    public ParallelMatchRuleProcessor(MatchRulesConfigBean config) {
        this(config, ForkJoinPool.commonPool(), false);
    }

    public void close() {
        shutdown();
    }

    /**
     * 关闭处理器及其线程池（如果拥有池的所有权）
     * 在不再使用处理器时应调用此方法以释放资源
     */
    public void shutdown() {
        // 只关闭自己拥有的ForkJoinPool
        if (isPoolOwner && forkJoinPool != null && !forkJoinPool.isShutdown()) {
            forkJoinPool.shutdown();
        }
    }

    /**
     * 异步检查字符串是否匹配规则
     *
     * @param input 要检查的字符串
     * @return 包含匹配结果的CompletableFuture
     */
    public CompletableFuture<Boolean> matchesAsync(String input) {
        return getMatchResultAsync(input).thenApply(MatchResult::isMatched);
    }

    /**
     * 检查字符串是否匹配规则
     *
     * @param input 要检查的字符串
     * @return 如果匹配返回true，否则返回false
     */
    public boolean matches(String input) {
        return matchesAsync(input).join();
    }

    /**
     * 获取匹配的原因（用于调试）
     *
     * @param input 要检查的字符串
     * @return 匹配原因的描述
     */
    public String getMatchReason(String input) {
        return getMatchResultAsync(input).join().getReason();
    }


    /**
     * 获取匹配结果和原因
     *
     * @param input 要检查的字符串
     * @return 包含匹配结果和原因的MatchResult对象
     */
    public CompletableFuture<MatchResult> getMatchResultAsync(String input) {
        if (input == null) {
            return CompletableFuture.completedFuture(new MatchResult(false, "输入为null"));
        }

        // 如果排除和包含规则都为空，默认匹配
        if (excludeProcessor.isEmpty() && includeProcessor.isEmpty()) {
            return CompletableFuture.completedFuture(new MatchResult(true, "默认匹配（没有设置任何规则）"));
        }

        // 如果只有包含规则
        if (excludeProcessor.isEmpty()) {
            return includeProcessor.getMatchResultAsync(input)
                    .thenApply(result -> {
                        if (result.isMatched()) {
                            return new MatchResult(true, "包含: " + result.getReason());
                        } else {
                            return new MatchResult(false, "不匹配包含规则");
                        }
                    });
        }

        // 如果只有排除规则
        if (includeProcessor.isEmpty()) {
            return excludeProcessor.getMatchResultAsync(input)
                    .thenApply(result -> {
                        if (result.isMatched()) {
                            return new MatchResult(false, "排除: " + result.getReason());
                        } else {
                            return new MatchResult(true, "默认包含（不匹配排除规则）");
                        }
                    });
        }

        // 既有排除规则又有包含规则
        return excludeProcessor.getMatchResultAsync(input)
                .thenCompose(excludeResult -> {
                    if (excludeResult.isMatched()) {
                        // 如果匹配排除规则，直接返回false
                        return CompletableFuture.completedFuture(new MatchResult(false, "排除: " + excludeResult.getReason()));
                    } else {
                        // 检查包含规则
                        return includeProcessor.getMatchResultAsync(input)
                                .thenApply(includeResult -> {
                                    if (includeResult.isMatched()) {
                                        return new MatchResult(true, "包含: " + includeResult.getReason());
                                    } else {
                                        return new MatchResult(false, "不匹配任何包含规则");
                                    }
                                });
                    }
                });
    }

}