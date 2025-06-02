package cn.citizenwiki.match.rule;

import cn.citizenwiki.match.MatchResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * 规则匹配器接口 - 定义规则匹配的行为
 */
public interface RuleMatcher {
    /**
     * 异步检查字符串是否匹配规则
     *
     * @param input        要检查的字符串
     * @param forkJoinPool Fork/Join线程池
     * @return 包含匹配结果的CompletableFuture
     */
    CompletableFuture<Boolean> matchAsync(String input, ForkJoinPool forkJoinPool);

    /**
     * 异步获取匹配结果及原因
     *
     * @param input        要检查的字符串
     * @param forkJoinPool Fork/Join线程池
     * @return 包含匹配结果的CompletableFuture
     */
    CompletableFuture<MatchResult> getMatchResultAsync(String input, ForkJoinPool forkJoinPool);

    /**
     * 检查规则匹配器是否为空
     *
     * @return 如果为空返回true，否则返回false
     */
    boolean isEmpty();
}