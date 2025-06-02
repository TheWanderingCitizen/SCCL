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
 * 后缀规则匹配器
 */
public class EndWithRuleMatcher implements RuleMatcher {
    private final Set<String> suffixes;

    public EndWithRuleMatcher(List<String> suffixes) {
        this.suffixes = suffixes != null ?
                Collections.unmodifiableSet(new HashSet<>(suffixes)) :
                Collections.emptySet();
    }

    @Override
    public CompletableFuture<Boolean> matchAsync(String input, ForkJoinPool forkJoinPool) {
        return getMatchResultAsync(input, forkJoinPool).thenApply(MatchResult::isMatched);
    }

    @Override
    public CompletableFuture<MatchResult> getMatchResultAsync(String input, ForkJoinPool forkJoinPool) {
        return CompletableFuture.supplyAsync(() -> {
            return suffixes.parallelStream()
                    .filter(input::endsWith)
                    .findAny()
                    .map(suffix -> MatchResult.matched("匹配后缀: " + suffix))
                    .orElse(MatchResult.notMatched());
        }, forkJoinPool);
    }

    @Override
    public boolean isEmpty() {
        return suffixes.isEmpty();
    }
}
    