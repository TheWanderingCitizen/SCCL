package cn.citizenwiki.config;

import java.util.Map;
import java.util.Set;

/**
 * 文本替换相关配置
 */
public class SearchableLocationReplaceConfig {

    private Set<String> ignoreKeys;

    private Map<String, String> overrideMappings;

    public Set<String> getIgnoreKeys() {
        return ignoreKeys;
    }

    public void setIgnoreKeys(Set<String> ignoreKeys) {
        this.ignoreKeys = ignoreKeys;
    }

    public Map<String, String> getOverrideMappings() {
        return overrideMappings;
    }

    public void setOverrideMappings(Map<String, String> overrideMappings) {
        this.overrideMappings = overrideMappings;
    }

    @Override
    public String toString() {
        return "SearchableLocationReplaceConfig{" +
                "ignoreKeys=" + ignoreKeys +
                ", overrideMappings=" + overrideMappings +
                '}';
    }
}
