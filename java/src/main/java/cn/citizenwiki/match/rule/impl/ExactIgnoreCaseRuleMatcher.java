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
 * 忽略大小写的精确匹配规则匹配器
 */
public class ExactIgnoreCaseRuleMatcher implements RuleMatcher {
    private final Set<String> exactMatches;

    public ExactIgnoreCaseRuleMatcher(List<String> exactMatches) {
        this.exactMatches = exactMatches != null ?
                Collections.unmodifiableSet(new HashSet<>(exactMatches)) :
                Collections.emptySet();
    }

    @Override
    public CompletableFuture<Boolean> matchAsync(String input, ForkJoinPool forkJoinPool) {
        return getMatchResultAsync(input, forkJoinPool).thenApply(MatchResult::isMatched);
    }

    @Override
    public CompletableFuture<MatchResult> getMatchResultAsync(String input, ForkJoinPool forkJoinPool) {
        return CompletableFuture.supplyAsync(() -> {
            return exactMatches.parallelStream()
                    .filter(pattern -> pattern.equalsIgnoreCase(input))
                    .findAny()
                    .map(pattern -> MatchResult.matched("忽略大小写精确匹配: " + pattern))
                    .orElse(MatchResult.notMatched());
        }, forkJoinPool);
    }

    @Override
    public boolean isEmpty() {
        return exactMatches.isEmpty();
    }
}