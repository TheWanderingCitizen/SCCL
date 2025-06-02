package cn.citizenwiki.model.config;

import cn.citizenwiki.match.rule.MatchRules;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 双语匹配规则配置类
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchRulesConfigBean {

    private MatchRules matchRules;

    // Getters and Setters
    public MatchRules getMatchRules() {
        return matchRules;
    }

    public void setMatchRules(MatchRules matchRules) {
        this.matchRules = matchRules;
    }
}