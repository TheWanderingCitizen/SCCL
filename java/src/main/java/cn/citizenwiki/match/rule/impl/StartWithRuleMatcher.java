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
 * 前缀规则匹配器
 */
public class StartWithRuleMatcher implements RuleMatcher {
    private final Set<String> prefixes;

    public StartWithRuleMatcher(List<String> prefixes) {
        this.prefixes = prefixes != null ?
                Collections.unmodifiableSet(new HashSet<>(prefixes)) :
                Collections.emptySet();
    }

    @Override
    public CompletableFuture<Boolean> matchAsync(String input, ForkJoinPool forkJoinPool) {
        return getMatchResultAsync(input, forkJoinPool).thenApply(MatchResult::isMatched);
    }

    @Override
    public CompletableFuture<MatchResult> getMatchResultAsync(String input, ForkJoinPool forkJoinPool) {
        return CompletableFuture.supplyAsync(() -> {
            return prefixes.parallelStream()
                    .filter(input::startsWith)
                    .findAny()
                    .map(prefix -> MatchResult.matched("匹配前缀: " + prefix))
                    .orElse(MatchResult.notMatched());
        }, forkJoinPool);
    }

    @Override
    public boolean isEmpty() {
        return prefixes.isEmpty();
    }
}