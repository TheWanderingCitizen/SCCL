package cn.citizenwiki.match.rule;

import cn.citizenwiki.match.MatchResult;
import cn.citizenwiki.match.rule.impl.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 规则组处理器 - 处理单个规则组（include 或 exclude）
 */
public class RuleMatcherGroupProcessor {
    private RuleMatcher[] ruleMatchers = new RuleMatcher[0];
    private final ForkJoinPool forkJoinPool;

    /**
     * 初始化规则组处理器
     *
     * @param ruleGroup    规则组配置
     * @param forkJoinPool Fork/Join线程池
     */
    public RuleMatcherGroupProcessor(RuleGroup ruleGroup, ForkJoinPool forkJoinPool) {
        this.forkJoinPool = forkJoinPool;

        if (ruleGroup == null) {
            return;
        }

        // 创建临时List用于构建数组
        List<RuleMatcher> tempList = new ArrayList<>();

        // 添加各种规则匹配器
        if (Objects.nonNull(ruleGroup.getRegex()) && !ruleGroup.getRegex().isEmpty()) {
            tempList.add(new RegexRuleMatcher(ruleGroup.getRegex()));
        }
        if (Objects.nonNull(ruleGroup.getStartWith()) && !ruleGroup.getStartWith().isEmpty()) {
            tempList.add(new StartWithRuleMatcher(ruleGroup.getStartWith()));
        }
        if (Objects.nonNull(ruleGroup.getStartWithIgnoreCase()) && !ruleGroup.getStartWithIgnoreCase().isEmpty()) {
            tempList.add(new StartWithIgnoreCaseMatcher(ruleGroup.getStartWithIgnoreCase()));
        }
        if (Objects.nonNull(ruleGroup.getEndWith()) && !ruleGroup.getEndWith().isEmpty()) {
            tempList.add(new EndWithRuleMatcher(ruleGroup.getEndWith()));
        }
        if (Objects.nonNull(ruleGroup.getEndWithIgnoreCase()) && !ruleGroup.getEndWithIgnoreCase().isEmpty()) {
            tempList.add(new EndWithIgnoreCaseMatcher(ruleGroup.getEndWithIgnoreCase()));
        }
        if (Objects.nonNull(ruleGroup.getEq()) && !ruleGroup.getEq().isEmpty()) {
            tempList.add(new ExactRuleMatcher(ruleGroup.getEq()));
        }
        if (Objects.nonNull(ruleGroup.getEqIgnoreCase()) && !ruleGroup.getEqIgnoreCase().isEmpty()) {
            tempList.add(new ExactIgnoreCaseRuleMatcher(ruleGroup.getEqIgnoreCase()));
        }
        if (Objects.nonNull(ruleGroup.getContains()) && !ruleGroup.getContains().isEmpty()) {
            tempList.add(new ContainsRuleMatcher(ruleGroup.getContains()));
        }
        if (Objects.nonNull(ruleGroup.getContainsIgnoreCase()) && !ruleGroup.getContainsIgnoreCase().isEmpty()) {
            tempList.add(new ContainsIgnoreCaseMatcher(ruleGroup.getContainsIgnoreCase()));
        }

        // 将List转换为数组
        ruleMatchers = tempList.toArray(new RuleMatcher[0]);
    }

    /**
     * 异步检查字符串是否匹配该规则组的任何规则
     *
     * @param input 要检查的字符串
     * @return 包含匹配结果的CompletableFuture
     */
    public CompletableFuture<Boolean> matchesAsync(String input) {
        return processMatchAsync(
                matcher -> matcher.matchAsync(input, forkJoinPool),
                false

        );
    }

    /**
     * 异步获取匹配结果及原因
     *
     * @param input 要检查的字符串
     * @return 包含匹配结果的CompletableFuture
     */
    public CompletableFuture<MatchResult> getMatchResultAsync(String input) {
        return processMatchAsync(
                matcher -> matcher.getMatchResultAsync(input, forkJoinPool),
                MatchResult.notMatched()
        );
    }

    /**
     * 通用的异步匹配处理方法
     *
     * @param matchFunction 匹配函数，将RuleMatcher转换为对应的CompletableFuture
     * @param defaultValue 当规则组为空时的默认返回值
     * @param <T> 返回值类型
     * @return 包含匹配结果的CompletableFuture
     */
    private <T> CompletableFuture<T> processMatchAsync(
            Function<RuleMatcher, CompletableFuture<T>> matchFunction,
            T defaultValue) {

        if (isEmpty()) {
            return CompletableFuture.completedFuture(defaultValue);
        }

        // 创建多个并行任务
        List<CompletableFuture<T>> futures = Arrays.stream(ruleMatchers)
                .map(matchFunction)
                .collect(Collectors.toList());

        // 任意一个任务返回成功，整体结果就为成功（短路操作）
        return anyMatchAsync(futures, defaultValue);
    }


    /**
     * 异步检查任意一个Future是否返回成功结果
     *
     * @param futures 要检查的Future列表
     * @param defaultValue 默认返回值
     * @param <T> 返回值类型
     * @return 包含结果的CompletableFuture
     */
    private <T> CompletableFuture<T> anyMatchAsync(
            List<CompletableFuture<T>> futures,
            T defaultValue) {

        // 创建一个新的CompletableFuture作为结果
        CompletableFuture<T> result = new CompletableFuture<>();

        // 计数器，用于跟踪已完成的Future数量
        AtomicInteger counter = new AtomicInteger(futures.size());

        // 为每个Future添加回调
        for (CompletableFuture<T> future : futures) {
            future.thenAccept(matchResult -> {
                // 判断是否为成功结果
                boolean isSuccess = isSuccessResult(matchResult);

                if (isSuccess) {
                    // 如果匹配，立即完成结果
                    result.complete(matchResult);
                } else {
                    // 如果不匹配，检查是否所有Future都已完成
                    if (counter.decrementAndGet() == 0 && !result.isDone()) {
                        result.complete(defaultValue);
                    }
                }
            }).exceptionally(ex -> {
                // 出现异常时，也减少计数
                if (counter.decrementAndGet() == 0 && !result.isDone()) {
                    result.complete(defaultValue);
                }
                return null;
            });
        }

        // 如果没有Future，则返回默认值
        if (futures.isEmpty()) {
            result.complete(defaultValue);
        }

        return result;
    }


    /**
     * 判断结果是否为成功结果
     */
    private <T> boolean isSuccessResult(T result) {
        if (result instanceof Boolean br) {
            return br;
        } else if (result instanceof MatchResult mr) {
            return mr.isMatched();
        }
        return false;
    }


    /**
     * 检查该规则组是否为空（没有任何规则）
     *
     * @return 如果为空返回true，否则返回false
     */
    public boolean isEmpty() {
        return ruleMatchers.length == 0;
    }
}