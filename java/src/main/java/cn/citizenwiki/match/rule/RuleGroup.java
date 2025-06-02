package cn.citizenwiki.match.rule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则组类
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleGroup {

    private List<String> regex;
    private List<String> startWith;
    private List<String> startWithIgnoreCase;
    private List<String> endWith;
    private List<String> endWithIgnoreCase;
    private List<String> eq;
    private List<String> eqIgnoreCase;
    private List<String> contains;
    private List<String> containsIgnoreCase;

    /**
     * 将另一个 RuleGroup 的规则添加到当前 RuleGroup 中
     */
    public void add(RuleGroup other) {
        if (other == null) {
            return;
        }

        addToList(this::getRegex, this::setRegex, other.getRegex());
        addToList(this::getStartWith, this::setStartWith, other.getStartWith());
        addToList(this::getStartWithIgnoreCase, this::setStartWithIgnoreCase, other.getStartWithIgnoreCase());
        addToList(this::getEndWith, this::setEndWith, other.getEndWith());
        addToList(this::getEndWithIgnoreCase, this::setEndWithIgnoreCase, other.getEndWithIgnoreCase());
        addToList(this::getEq, this::setEq, other.getEq());
        addToList(this::getEqIgnoreCase, this::setEqIgnoreCase, other.getEqIgnoreCase());
        addToList(this::getContains, this::setContains, other.getContains());
        addToList(this::getContainsIgnoreCase, this::setContainsIgnoreCase, other.getContainsIgnoreCase());
    }

    /**
     * 辅助方法：将源列表添加到目标列表中
     */
    private void addToList(java.util.function.Supplier<List<String>> getter,
                           java.util.function.Consumer<List<String>> setter,
                           List<String> sourceList) {
        if (sourceList != null && !sourceList.isEmpty()) {
            List<String> targetList = getter.get();
            if (targetList == null) {
                targetList = new ArrayList<>();
                setter.accept(targetList);
            }
            targetList.addAll(sourceList);
        }
    }

    // Getters and Setters
    public List<String> getRegex() {
        return regex;
    }

    public void setRegex(List<String> regex) {
        this.regex = regex;
    }

    public List<String> getStartWith() {
        return startWith;
    }

    public void setStartWith(List<String> startWith) {
        this.startWith = startWith;
    }

    public List<String> getEndWith() {
        return endWith;
    }

    public void setEndWith(List<String> endWith) {
        this.endWith = endWith;
    }

    public List<String> getEq() {
        return eq;
    }

    public void setEq(List<String> eq) {
        this.eq = eq;
    }

    public List<String> getEqIgnoreCase() {
        return eqIgnoreCase;
    }

    public void setEqIgnoreCase(List<String> eqIgnoreCase) {
        this.eqIgnoreCase = eqIgnoreCase;
    }

    public List<String> getContains() {
        return contains;
    }

    public void setContains(List<String> contains) {
        this.contains = contains;
    }

    public List<String> getStartWithIgnoreCase() {
        return startWithIgnoreCase;
    }

    public void setStartWithIgnoreCase(List<String> startWithIgnoreCase) {
        this.startWithIgnoreCase = startWithIgnoreCase;
    }

    public List<String> getEndWithIgnoreCase() {
        return endWithIgnoreCase;
    }

    public void setEndWithIgnoreCase(List<String> endWithIgnoreCase) {
        this.endWithIgnoreCase = endWithIgnoreCase;
    }

    public List<String> getContainsIgnoreCase() {
        return containsIgnoreCase;
    }

    public void setContainsIgnoreCase(List<String> containsIgnoreCase) {
        this.containsIgnoreCase = containsIgnoreCase;
    }
}