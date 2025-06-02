package cn.citizenwiki.match.rule.impl;

import cn.citizenwiki.match.MatchResult;
import cn.citizenwiki.match.rule.RuleMatcher;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * 包含指定字符串匹配器（忽略大小写）
 */
public class ContainsIgnoreCaseMatcher implements RuleMatcher {

    private final List<String> substrings;

    public ContainsIgnoreCaseMatcher(List<String> substrings) {
        this.substrings = substrings;
    }

    @Override
    public CompletableFuture<Boolean> matchAsync(String input, ForkJoinPool forkJoinPool) {
        return getMatchResultAsync(input, forkJoinPool).thenApply(MatchResult::isMatched);
    }

    @Override
    public CompletableFuture<MatchResult> getMatchResultAsync(String input, ForkJoinPool forkJoinPool) {
        return CompletableFuture.supplyAsync(() -> {
            if (input == null) {
                return new MatchResult(false, "输入为null");
            }
            
            if (isEmpty()) {
                return new MatchResult(false, "规则为空");
            }
            
            String lowerInput = input.toLowerCase();
            for (String substring : substrings) {
                if (substring != null && lowerInput.contains(substring.toLowerCase())) {
                    return new MatchResult(true, String.format("包含\"%s\"（忽略大小写）", substring));
                }
            }
            
            return new MatchResult(false, String.format("不包含%s（忽略大小写）", substrings));
        }, forkJoinPool);
    }

    @Override
    public boolean isEmpty() {
        return substrings == null || substrings.isEmpty();
    }
}