package cn.citizenwiki.match;

import cn.citizenwiki.match.rule.ConfigProvider;
import cn.citizenwiki.match.rule.MatchRules;
import cn.citizenwiki.match.rule.RuleGroup;
import cn.citizenwiki.model.config.MatchRulesConfigBean;
import cn.citizenwiki.model.config.TranslationRuleConfigBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TranslationRuleProcessor 单元测试")
class TranslationRuleProcessorTest {

    private TranslationRuleProcessor processor;

    @Mock
    private ConfigProvider<MatchRulesConfigBean> mockMatchRulesConfigProvider;

    @BeforeEach
    void setUp() {
        // 使用 mock 的 ConfigProvider，避免触发静态代码块
    }

    @Test
    @DisplayName("测试 fromTranslationRuleConfig 创建处理器")
    void testFromTranslationRuleConfig() {
        // Given
        TranslationRuleConfigBean config = createTranslationRuleConfig();

        // When
        processor = TranslationRuleProcessor.fromTranslationRuleConfig(config, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        assertTrue(processor.isMatch("test_key", "test_original", "test_translation"));
    }

    @Test
    @DisplayName("测试直接构造函数创建处理器")
    void testDirectConstructor() {
        // Given
        MatchRulesConfigBean keyRules = createKeyRules();
        MatchRulesConfigBean originalRules = createOriginalRules();
        MatchRulesConfigBean translationRules = createTranslationRules();

        // When
        processor = new TranslationRuleProcessor(keyRules, originalRules, translationRules, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        assertTrue(processor.isMatch("test_key", "test_original", "test_translation"));
    }

    @Test
    @DisplayName("测试无imports配置的情况")
    void testNoImports() {
        // Given
        TranslationRuleConfigBean config = createTranslationRuleConfig();
        // 确保没有imports

        // When
        processor = TranslationRuleProcessor.fromTranslationRuleConfig(config, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        assertTrue(processor.isMatch("test_key", "test_original", "test_translation"));
    }

    @Test
    @DisplayName("测试空imports列表的情况")
    void testEmptyImports() {
        // Given
        TranslationRuleConfigBean config = createTranslationRuleConfig();
        setImports(config.getKey(), Collections.emptyList());
        setImports(config.getOriginal(), Collections.emptyList());
        setImports(config.getTranslation(), Collections.emptyList());

        // When
        processor = TranslationRuleProcessor.fromTranslationRuleConfig(config, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        assertTrue(processor.isMatch("test_key", "test_original", "test_translation"));
    }

    @Test
    @DisplayName("测试单个import文件合并")
    void testSingleImport() {
        // Given
        TranslationRuleConfigBean config = createTranslationRuleConfig();
        setImports(config.getKey(), Arrays.asList("key_import.yaml"));

        MatchRulesConfigBean keyImportConfig = createImportKeyRules();
        when(mockMatchRulesConfigProvider.getConfig("key_import.yaml")).thenReturn(keyImportConfig);

        // When
        processor = TranslationRuleProcessor.fromTranslationRuleConfig(config, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        // 验证合并后的规则生效 - 既能匹配原有规则，也能匹配导入规则
        assertTrue(processor.isMatch("test_key", "test_original", "test_translation"));
        assertTrue(processor.isMatch("imported_key", "test_original", "test_translation"));
    }

    @Test
    @DisplayName("测试多个import文件合并")
    void testMultipleImports() {
        // Given
        TranslationRuleConfigBean config = createTranslationRuleConfig();
        setImports(config.getKey(), Arrays.asList("key_import1.yaml", "key_import2.yaml"));

        MatchRulesConfigBean keyImport1 = createImportKeyRules();
        MatchRulesConfigBean keyImport2 = createSecondImportKeyRules();

        when(mockMatchRulesConfigProvider.getConfig("key_import1.yaml")).thenReturn(keyImport1);
        when(mockMatchRulesConfigProvider.getConfig("key_import2.yaml")).thenReturn(keyImport2);

        // When
        processor = TranslationRuleProcessor.fromTranslationRuleConfig(config, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        // 验证所有规则都被合并
        assertTrue(processor.isMatch("test_key", "test_original", "test_translation"));
        assertTrue(processor.isMatch("imported_key", "test_original", "test_translation"));
        assertTrue(processor.isMatch("second_key", "test_original", "test_translation"));
    }

    @Test
    @DisplayName("测试嵌套import文件合并")
    void testNestedImports() {
        // Given
        TranslationRuleConfigBean config = createTranslationRuleConfig();
        setImports(config.getKey(), Arrays.asList("key_import.yaml"));

        MatchRulesConfigBean keyImportConfig = createImportKeyRules();
        setImports(keyImportConfig, Arrays.asList("nested_import.yaml"));

        MatchRulesConfigBean nestedImportConfig = createNestedImportKeyRules();

        when(mockMatchRulesConfigProvider.getConfig("key_import.yaml")).thenReturn(keyImportConfig);
        when(mockMatchRulesConfigProvider.getConfig("nested_import.yaml")).thenReturn(nestedImportConfig);

        // When
        processor = TranslationRuleProcessor.fromTranslationRuleConfig(config, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        // 验证嵌套导入的规则生效
        assertTrue(processor.isMatch("test_key", "test_original", "test_translation"));
        assertTrue(processor.isMatch("imported_key", "test_original", "test_translation"));
        assertTrue(processor.isMatch("nested_key", "test_original", "test_translation"));
    }

    @Test
    @DisplayName("测试循环引用检测 - 直接循环")
    void testDirectCircularReference() {
        // Given
        TranslationRuleConfigBean config = createTranslationRuleConfig();
        setImports(config.getKey(), Arrays.asList("circular.yaml"));

        MatchRulesConfigBean circularConfig = createImportKeyRules();
        setImports(circularConfig, Arrays.asList("circular.yaml")); // 自引用

        when(mockMatchRulesConfigProvider.getConfig("circular.yaml")).thenReturn(circularConfig);

        // When
        processor = TranslationRuleProcessor.fromTranslationRuleConfig(config, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        // 验证循环引用被正确处理，不会导致无限递归
        assertTrue(processor.isMatch("test_key", "test_original", "test_translation"));
        assertTrue(processor.isMatch("imported_key", "test_original", "test_translation"));
    }

    @Test
    @DisplayName("测试循环引用检测 - 间接循环")
    void testIndirectCircularReference() {
        // Given
        TranslationRuleConfigBean config = createTranslationRuleConfig();
        setImports(config.getKey(), Arrays.asList("import1.yaml"));

        MatchRulesConfigBean import1Config = createImportKeyRules();
        setImports(import1Config, Arrays.asList("import2.yaml"));

        MatchRulesConfigBean import2Config = createSecondImportKeyRules();
        setImports(import2Config, Arrays.asList("import1.yaml")); // 循环引用

        when(mockMatchRulesConfigProvider.getConfig("import1.yaml")).thenReturn(import1Config);
        when(mockMatchRulesConfigProvider.getConfig("import2.yaml")).thenReturn(import2Config);

        // When
        processor = TranslationRuleProcessor.fromTranslationRuleConfig(config, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        // 验证间接循环引用被正确检测和处理
        assertTrue(processor.isMatch("test_key", "test_original", "test_translation"));
        assertTrue(processor.isMatch("imported_key", "test_original", "test_translation"));
        assertTrue(processor.isMatch("second_key", "test_original", "test_translation"));
    }

    @Test
    @DisplayName("测试导入文件不存在的情况")
    void testImportFileNotFound() {
        // Given
        TranslationRuleConfigBean config = createTranslationRuleConfig();
        setImports(config.getKey(), Arrays.asList("nonexistent.yaml", "key_import.yaml"));

        MatchRulesConfigBean keyImportConfig = createImportKeyRules();

        when(mockMatchRulesConfigProvider.getConfig("nonexistent.yaml")).thenReturn(null);
        when(mockMatchRulesConfigProvider.getConfig("key_import.yaml")).thenReturn(keyImportConfig);

        // When
        processor = TranslationRuleProcessor.fromTranslationRuleConfig(config, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        // 验证找到的文件仍然被正确处理
        assertTrue(processor.isMatch("test_key", "test_original", "test_translation"));
        assertTrue(processor.isMatch("imported_key", "test_original", "test_translation"));
    }

    @Test
    @DisplayName("测试null配置的处理")
    void testNullRules() {
        // Given
        MatchRulesConfigBean nullKeyRules = null;
        MatchRulesConfigBean originalRules = createOriginalRules();
        MatchRulesConfigBean translationRules = createTranslationRules();

        // When
        processor = new TranslationRuleProcessor(nullKeyRules, originalRules, translationRules, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        // null规则应该允许所有内容通过（对于该字段）
        assertTrue(processor.isMatch("any_key", "test_original", "test_translation"));
    }

    @Test
    @DisplayName("测试空MatchRules的处理")
    void testEmptyMatchRules() {
        // Given
        MatchRulesConfigBean emptyKeyRules = new MatchRulesConfigBean();
        emptyKeyRules.setMatchRules(new MatchRules()); // 空的MatchRules

        MatchRulesConfigBean originalRules = createOriginalRules();
        MatchRulesConfigBean translationRules = createTranslationRules();

        // When
        processor = new TranslationRuleProcessor(emptyKeyRules, originalRules, translationRules, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        // 空规则应该允许所有内容通过（对于该字段）
        assertTrue(processor.isMatch("any_key", "test_original", "test_translation"));
    }

    @Test
    @DisplayName("测试不匹配的情况")
    void testNotMatch() {
        // Given
        TranslationRuleConfigBean config = createTranslationRuleConfig();
        processor = TranslationRuleProcessor.fromTranslationRuleConfig(config, mockMatchRulesConfigProvider);

        // When & Then
        // key不匹配（不以test_开头）
        assertFalse(processor.isMatch("other_key", "test_original", "test_translation"));

        // original不匹配（不包含original）
        assertFalse(processor.isMatch("test_key", "test_text", "test_translation"));

        // translation不匹配（不包含translation）
        assertFalse(processor.isMatch("test_key", "test_original", "test_text"));
    }

    @Test
    @DisplayName("测试匹配原因获取")
    void testGetMatchReason() {
        // Given
        TranslationRuleConfigBean config = createTranslationRuleConfig();
        processor = TranslationRuleProcessor.fromTranslationRuleConfig(config, mockMatchRulesConfigProvider);

        // When
        String reason = processor.getMatchReason("test_key", "test_original", "test_translation");

        // Then
        assertNotNull(reason);
        assertTrue(reason.contains("key："));
        assertTrue(reason.contains("original："));
        assertTrue(reason.contains("translation："));
    }

    @Test
    @DisplayName("测试复杂场景 - 不同字段有不同的imports")
    void testDifferentImportsForDifferentFields() {
        // Given
        TranslationRuleConfigBean config = createTranslationRuleConfig();
        setImports(config.getKey(), Arrays.asList("key_import.yaml"));
        setImports(config.getOriginal(), Arrays.asList("original_import.yaml"));
        setImports(config.getTranslation(), Arrays.asList("translation_import.yaml"));

        MatchRulesConfigBean keyImportConfig = createImportKeyRules();
        MatchRulesConfigBean originalImportConfig = createImportOriginalRules();
        MatchRulesConfigBean translationImportConfig = createImportTranslationRules();

        when(mockMatchRulesConfigProvider.getConfig("key_import.yaml")).thenReturn(keyImportConfig);
        when(mockMatchRulesConfigProvider.getConfig("original_import.yaml")).thenReturn(originalImportConfig);
        when(mockMatchRulesConfigProvider.getConfig("translation_import.yaml")).thenReturn(translationImportConfig);

        // When
        processor = TranslationRuleProcessor.fromTranslationRuleConfig(config, mockMatchRulesConfigProvider);

        // Then
        assertNotNull(processor);
        // 验证各字段的导入规则都生效
        assertTrue(processor.isMatch("test_key", "test_original", "test_translation"));
        assertTrue(processor.isMatch("imported_key", "test_original", "test_translation"));
        assertTrue(processor.isMatch("test_key", "imported_original", "test_translation"));
        assertTrue(processor.isMatch("test_key", "test_original", "imported_translation"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 为 MatchRulesConfigBean 设置 imports
     */
    private void setImports(MatchRulesConfigBean config, List<String> imports) {
        if (config != null && config.getMatchRules() != null) {
            config.getMatchRules().setImports(imports);
        }
    }

    /**
     * 创建完整的 TranslationRuleConfigBean
     */
    private TranslationRuleConfigBean createTranslationRuleConfig() {
        TranslationRuleConfigBean config = new TranslationRuleConfigBean();
        config.setKey(createKeyRules());
        config.setOriginal(createOriginalRules());
        config.setTranslation(createTranslationRules());
        config.setExt(new HashMap<>());
        return config;
    }

    /**
     * 创建 key 规则
     */
    private MatchRulesConfigBean createKeyRules() {
        MatchRulesConfigBean keyRule = new MatchRulesConfigBean();
        MatchRules keyMatchRules = new MatchRules();
        RuleGroup keyInclude = new RuleGroup();
        keyInclude.setStartWith(Arrays.asList("test_"));
        keyMatchRules.setInclude(keyInclude);
        keyRule.setMatchRules(keyMatchRules);
        return keyRule;
    }

    /**
     * 创建 original 规则
     */
    private MatchRulesConfigBean createOriginalRules() {
        MatchRulesConfigBean originalRule = new MatchRulesConfigBean();
        MatchRules originalMatchRules = new MatchRules();
        RuleGroup originalInclude = new RuleGroup();
        originalInclude.setContains(Arrays.asList("original"));
        originalMatchRules.setInclude(originalInclude);
        originalRule.setMatchRules(originalMatchRules);
        return originalRule;
    }

    /**
     * 创建 translation 规则
     */
    private MatchRulesConfigBean createTranslationRules() {
        MatchRulesConfigBean translationRule = new MatchRulesConfigBean();
        MatchRules translationMatchRules = new MatchRules();
        RuleGroup translationInclude = new RuleGroup();
        translationInclude.setContains(Arrays.asList("translation"));
        translationMatchRules.setInclude(translationInclude);
        translationRule.setMatchRules(translationMatchRules);
        return translationRule;
    }

    /**
     * 创建导入的 key 规则
     */
    private MatchRulesConfigBean createImportKeyRules() {
        MatchRulesConfigBean keyRule = new MatchRulesConfigBean();
        MatchRules keyMatchRules = new MatchRules();
        RuleGroup keyInclude = new RuleGroup();
        keyInclude.setStartWith(Arrays.asList("imported_"));
        keyMatchRules.setInclude(keyInclude);
        keyRule.setMatchRules(keyMatchRules);
        return keyRule;
    }

    /**
     * 创建第二个导入的 key 规则
     */
    private MatchRulesConfigBean createSecondImportKeyRules() {
        MatchRulesConfigBean keyRule = new MatchRulesConfigBean();
        MatchRules keyMatchRules = new MatchRules();
        RuleGroup keyInclude = new RuleGroup();
        keyInclude.setStartWith(Arrays.asList("second_"));
        keyMatchRules.setInclude(keyInclude);
        keyRule.setMatchRules(keyMatchRules);
        return keyRule;
    }

    /**
     * 创建嵌套导入的 key 规则
     */
    private MatchRulesConfigBean createNestedImportKeyRules() {
        MatchRulesConfigBean keyRule = new MatchRulesConfigBean();
        MatchRules keyMatchRules = new MatchRules();
        RuleGroup keyInclude = new RuleGroup();
        keyInclude.setStartWith(Arrays.asList("nested_"));
        keyMatchRules.setInclude(keyInclude);
        keyRule.setMatchRules(keyMatchRules);
        return keyRule;
    }

    /**
     * 创建导入的 original 规则
     */
    private MatchRulesConfigBean createImportOriginalRules() {
        MatchRulesConfigBean originalRule = new MatchRulesConfigBean();
        MatchRules originalMatchRules = new MatchRules();
        RuleGroup originalInclude = new RuleGroup();
        originalInclude.setContains(Arrays.asList("imported_original"));
        originalMatchRules.setInclude(originalInclude);
        originalRule.setMatchRules(originalMatchRules);
        return originalRule;
    }

    /**
     * 创建导入的 translation 规则
     */
    private MatchRulesConfigBean createImportTranslationRules() {
        MatchRulesConfigBean translationRule = new MatchRulesConfigBean();
        MatchRules translationMatchRules = new MatchRules();
        RuleGroup translationInclude = new RuleGroup();
        translationInclude.setContains(Arrays.asList("imported_translation"));
        translationMatchRules.setInclude(translationInclude);
        translationRule.setMatchRules(translationMatchRules);
        return translationRule;
    }
}