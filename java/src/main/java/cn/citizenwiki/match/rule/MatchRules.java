package cn.citizenwiki.match.rule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 匹配规则类
 *
 * 支持包含规则、排除规则以及导入机制。
 * 导入机制允许从其他配置文件中继承规则，支持循环引用检测。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchRules {

    private static final Logger logger = LoggerFactory.getLogger(MatchRules.class);

    private RuleGroup include;
    private RuleGroup exclude;
    /** 导入其他规则文件的配置 */
    private List<String> imports;


    /**
     * 合并导入的规则
     *
     * @param configProvider 配置提供者
     * @param ruleExtractor 规则提取器
     * @param <T> 配置对象类型
     * @return 合并后的新 MatchRules 对象
     */
    public <T> MatchRules mergeWithImports(ConfigProvider<T> configProvider, RuleExtractor<T> ruleExtractor) {
        return mergeWithImports(configProvider, ruleExtractor, new HashSet<>(), null);
    }

    /**
     * 带循环检测的规则合并
     *
     * @param configProvider 配置提供者
     * @param ruleExtractor 规则提取器
     * @param processedFiles 已处理的文件集合，用于循环检测
     * @param currentFileName 当前文件名
     * @param <T> 配置对象类型
     * @return 合并后的新 MatchRules 对象
     */
    private <T> MatchRules mergeWithImports(ConfigProvider<T> configProvider,
                                            RuleExtractor<T> ruleExtractor,
                                            Set<String> processedFiles,
                                            String currentFileName) {
        // 创建合并结果
        MatchRules mergedRules = new MatchRules();

        // 深拷贝当前规则作为基础
        if (this.include != null) {
            mergedRules.include = copyRuleGroup(this.include);
        }
        if (this.exclude != null) {
            mergedRules.exclude = copyRuleGroup(this.exclude);
        }

        // 处理导入
        if (this.imports != null && !this.imports.isEmpty()) {
            for (String importFileName : this.imports) {
                // 循环引用检测
                if (processedFiles.contains(importFileName)) {
                    logger.warn("检测到循环引用：{} -> {}，跳过处理", currentFileName, importFileName);
                    continue;
                }

                // 创建新的处理路径
                Set<String> newProcessedFiles = new HashSet<>(processedFiles);
                if (currentFileName != null) {
                    newProcessedFiles.add(currentFileName);
                }

                // 获取导入的配置
                T importedConfig = configProvider.getConfig(importFileName);
                if (importedConfig != null) {
                    // 提取规则
                    MatchRules importedRules = ruleExtractor.extractRules(importedConfig);
                    if (importedRules != null) {
                        // 递归处理导入文件的 imports
                        MatchRules processedImportedRules = importedRules.mergeWithImports(
                                configProvider, ruleExtractor, newProcessedFiles, importFileName);

                        // 合并规则
                        mergedRules.mergeWith(processedImportedRules);
                    }
                } else {
                    logger.warn("无法找到导入的规则文件：{}", importFileName);
                }
            }
        }

        return mergedRules;
    }

    /**
     * 将另一个 MatchRules 对象的规则合并到当前对象中
     *
     * 合并策略：
     * 1. include 规则：将源的 include 规则添加到当前的 include 中
     * 2. exclude 规则：将源的 exclude 规则添加到当前的 exclude 中
     * 3. imports 规则：将源的 imports 添加到当前的 imports 中（去重）
     *
     * @param source 要合并的源 MatchRules 对象
     */
    public void mergeWith(MatchRules source) {
        if (source == null) {
            return;
        }

        // 合并 include 规则
        if (source.getInclude() != null) {
            if (this.include == null) {
                this.include = new RuleGroup();
            }
            this.include.add(source.getInclude());
        }

        // 合并 exclude 规则
        if (source.getExclude() != null) {
            if (this.exclude == null) {
                this.exclude = new RuleGroup();
            }
            this.exclude.add(source.getExclude());
        }

        // 合并 imports 规则（去重）
        if (source.getImports() != null && !source.getImports().isEmpty()) {
            if (this.imports == null) {
                this.imports = new ArrayList<>();
            }
            for (String importFile : source.getImports()) {
                if (!this.imports.contains(importFile)) {
                    this.imports.add(importFile);
                }
            }
        }
    }

    /**
     * 深拷贝 RuleGroup
     */
    private RuleGroup copyRuleGroup(RuleGroup source) {
        if (source == null) {
            return null;
        }

        RuleGroup copy = new RuleGroup();
        copy.setRegex(copyList(source.getRegex()));
        copy.setStartWith(copyList(source.getStartWith()));
        copy.setStartWithIgnoreCase(copyList(source.getStartWithIgnoreCase()));
        copy.setEndWith(copyList(source.getEndWith()));
        copy.setEndWithIgnoreCase(copyList(source.getEndWithIgnoreCase()));
        copy.setEq(copyList(source.getEq()));
        copy.setEqIgnoreCase(copyList(source.getEqIgnoreCase()));
        copy.setContains(copyList(source.getContains()));
        copy.setContainsIgnoreCase(copyList(source.getContainsIgnoreCase()));
        return copy;
    }

    /**
     * 深拷贝字符串列表
     */
    private List<String> copyList(List<String> source) {
        if (source == null) {
            return null;
        }
        return new ArrayList<>(source);
    }

    /**
     * 静态方法：将源 MatchRules 合并到目标 MatchRules 中
     *
     * @param target 目标 MatchRules 对象，合并结果会写入此对象
     * @param source 源 MatchRules 对象，其内容会被合并到目标中
     */
    public static void merge(MatchRules target, MatchRules source) {
        if (target != null) {
            target.mergeWith(source);
        }
    }

    // Getters and Setters
    public RuleGroup getInclude() {
        return include;
    }

    public void setInclude(RuleGroup include) {
        this.include = include;
    }

    public RuleGroup getExclude() {
        return exclude;
    }

    public void setExclude(RuleGroup exclude) {
        this.exclude = exclude;
    }

    public List<String> getImports() {
        return imports;
    }

    public void setImports(List<String> imports) {
        this.imports = imports;
    }
}