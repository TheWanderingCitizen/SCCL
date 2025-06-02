package cn.citizenwiki.match.rule.impl;

import cn.citizenwiki.match.MatchResult;
import cn.citizenwiki.match.rule.RuleMatcher;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * 以指定字符串结尾匹配器（忽略大小写）
 */
public class EndWithIgnoreCaseMatcher implements RuleMatcher {

    private final List<String> suffixes;

    public EndWithIgnoreCaseMatcher(List<String> suffixes) {
        this.suffixes = suffixes;
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
            
            for (String suffix : suffixes) {
                if (suffix != null && input.toLowerCase().endsWith(suffix.toLowerCase())) {
                    return new MatchResult(true, String.format("以\"%s\"结尾（忽略大小写）", suffix));
                }
            }
            
            return new MatchResult(false, String.format("不以%s结尾（忽略大小写）", suffixes));
        }, forkJoinPool);
    }

    @Override
    public boolean isEmpty() {
        return suffixes == null || suffixes.isEmpty();
    }
}