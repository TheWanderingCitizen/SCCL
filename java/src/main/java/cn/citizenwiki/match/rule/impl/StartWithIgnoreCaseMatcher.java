package cn.citizenwiki.match.rule.impl;

import cn.citizenwiki.match.MatchResult;
import cn.citizenwiki.match.rule.RuleMatcher;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * 以指定字符串开头匹配器（忽略大小写）
 */
public class StartWithIgnoreCaseMatcher implements RuleMatcher {

    private final List<String> prefixes;

    public StartWithIgnoreCaseMatcher(List<String> prefixes) {
        this.prefixes = prefixes;
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
            
            for (String prefix : prefixes) {
                if (prefix != null && input.toLowerCase().startsWith(prefix.toLowerCase())) {
                    return new MatchResult(true, String.format("以\"%s\"开头（忽略大小写）", prefix));
                }
            }
            
            return new MatchResult(false, String.format("不以%s开头（忽略大小写）", prefixes));
        }, forkJoinPool);
    }

    @Override
    public boolean isEmpty() {
        return prefixes == null || prefixes.isEmpty();
    }
}