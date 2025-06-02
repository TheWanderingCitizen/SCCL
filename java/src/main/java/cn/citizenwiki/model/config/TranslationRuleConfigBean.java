package cn.citizenwiki.model.config;

import java.util.Map;

public class TranslationRuleConfigBean {
    private MatchRulesConfigBean key;
    private MatchRulesConfigBean original;
    private MatchRulesConfigBean translation;
    private Map<String, MatchRulesConfigBean> ext;

    public MatchRulesConfigBean getKey() {
        return key;
    }

    public void setKey(MatchRulesConfigBean key) {
        this.key = key;
    }

    public MatchRulesConfigBean getOriginal() {
        return original;
    }

    public void setOriginal(MatchRulesConfigBean original) {
        this.original = original;
    }

    public MatchRulesConfigBean getTranslation() {
        return translation;
    }

    public void setTranslation(MatchRulesConfigBean translation) {
        this.translation = translation;
    }

    public Map<String, MatchRulesConfigBean> getExt() {
        return ext;
    }

    public void setExt(Map<String, MatchRulesConfigBean> ext) {
        this.ext = ext;
    }
}
