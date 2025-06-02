package cn.citizenwiki.match.rule.impl;

import cn.citizenwiki.match.MatchResult;
import cn.citizenwiki.match.rule.RuleMatcher;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * 精确匹配规则匹配器
 */
public class ExactRuleMatcher implements RuleMatcher {
    private final Set<String> exactMatches;

    public ExactRuleMatcher(List<String> exactMatches) {
        this.exactMatches = exactMatches != null ?
                Collections.unmodifiableSet(new HashSet<>(exactMatches)) :
                Collections.emptySet();
    }

    @Override
    public CompletableFuture<Boolean> matchAsync(String input, ForkJoinPool forkJoinPool) {
        // 对于HashSet.contains操作，不需要并行处理，直接在ForkJoinPool中执行即可
        return getMatchResultAsync(input, forkJoinPool).thenApply(MatchResult::isMatched);
    }

    @Override
    public CompletableFuture<MatchResult> getMatchResultAsync(String input, ForkJoinPool forkJoinPool) {
        return CompletableFuture.supplyAsync(() -> {
            if (exactMatches.contains(input)) {
                return MatchResult.matched("精确匹配: " + input);
            }
            return MatchResult.notMatched();
        }, forkJoinPool);
    }

    @Override
    public boolean isEmpty() {
        return exactMatches.isEmpty();
    }
}