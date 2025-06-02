package cn.citizenwiki.match.rule.impl;

import cn.citizenwiki.match.MatchResult;
import cn.citizenwiki.match.rule.RuleMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;

/**
 * 正则表达式规则匹配器
 */
public class RegexRuleMatcher implements RuleMatcher {

    private static final Logger logger = LoggerFactory.getLogger(RegexRuleMatcher.class);

    private final Set<Pattern> patterns;

    public RegexRuleMatcher(List<String> regexPatterns) {
        Set<Pattern> tempPatterns = new HashSet<>();
        if (regexPatterns != null) {
            for (String regex : regexPatterns) {
                try {
                    tempPatterns.add(Pattern.compile(regex));
                } catch (Exception e) {
                    logger.error("无效的正则表达式: [{}]", regex, e);
                    throw new RuntimeException("无效的正则表达式["+ regex +"]");
                }
            }
        }
        this.patterns = Collections.unmodifiableSet(tempPatterns);
    }

    @Override
    public CompletableFuture<Boolean> matchAsync(String input, ForkJoinPool forkJoinPool) {
        return getMatchResultAsync(input, forkJoinPool).thenApply(MatchResult::isMatched);
    }

    @Override
    public CompletableFuture<MatchResult> getMatchResultAsync(String input, ForkJoinPool forkJoinPool) {
        return CompletableFuture.supplyAsync(() -> {
            // 使用并行流查找匹配的模式
            return patterns.parallelStream()
                    .filter(pattern ->
                            pattern.matcher(input).matches())
                    .findAny()
                    .map(pattern ->
                            MatchResult.matched("匹配正则表达式: " + pattern.pattern()))
                    .orElse(MatchResult.notMatched());
        }, forkJoinPool);
    }

    @Override
    public boolean isEmpty() {
        return patterns.isEmpty();
    }
}