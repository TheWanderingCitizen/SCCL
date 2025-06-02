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
 * 包含规则匹配器
 */
public class ContainsRuleMatcher implements RuleMatcher {

    private final Set<String> containsPatterns;

    public ContainsRuleMatcher(List<String> containsPatterns) {
        this.containsPatterns = containsPatterns != null ?
                Collections.unmodifiableSet(new HashSet<>(containsPatterns)) :
                Collections.emptySet();
    }

    @Override
    public CompletableFuture<Boolean> matchAsync(String input, ForkJoinPool forkJoinPool) {
        return getMatchResultAsync(input, forkJoinPool).thenApply(MatchResult::isMatched);
    }

    @Override
    public CompletableFuture<MatchResult> getMatchResultAsync(String input, ForkJoinPool forkJoinPool) {
        return CompletableFuture.supplyAsync(() -> {
            return containsPatterns.parallelStream()
                    .filter(input::contains)
                    .findAny()
                    .map(pattern -> MatchResult.matched("包含子字符串: " + pattern))
                    .orElse(MatchResult.notMatched());
        }, forkJoinPool);
    }

    @Override
    public boolean isEmpty() {
        return containsPatterns.isEmpty();
    }
}