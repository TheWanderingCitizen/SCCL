package cn.citizenwiki.match;

/**
 * 匹配结果类，包含是否匹配及匹配原因
 */
public class MatchResult {
    private final boolean matched;
    private final String reason;

    public MatchResult(boolean matched, String reason) {
        this.matched = matched;
        this.reason = reason;
    }

    public static MatchResult notMatched() {
        return new MatchResult(false, null);
    }

    public static MatchResult matched(String reason) {
        return new MatchResult(true, reason);
    }

    public boolean isMatched() {
        return matched;
    }

    public String getReason() {
        return reason;
    }
}