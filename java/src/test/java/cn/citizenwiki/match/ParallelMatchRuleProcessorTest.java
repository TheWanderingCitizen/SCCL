package cn.citizenwiki.match;

import cn.citizenwiki.match.rule.MatchRules;
import cn.citizenwiki.match.rule.RuleGroup;
import cn.citizenwiki.model.config.MatchRulesConfigBean;
import cn.citizenwiki.model.config.TranslationRuleConfigBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class ParallelMatchRuleProcessorTest {

    private ObjectMapper objectMapper;
    private File tempRuleFile;

    @BeforeEach
    public void setup() throws IOException {
        objectMapper = new ObjectMapper();
        // 配置ObjectMapper使用下划线命名策略，将驼峰转换为下划线
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        tempRuleFile = File.createTempFile("match_rules", ".json");
        tempRuleFile.deleteOnExit();
    }

    @Test
    public void testRegexIncludeRule() throws IOException, ExecutionException, InterruptedException {
        // 生成JSON配置文件（属性下划线分隔）
        String json = """
                {
                  "match_rules": {
                    "include": {
                      "regex": ["^A.*Z$"]
                    }
                  }
                }
                """;
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));
        // 加载配置并创建处理器
        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);
        // 测试匹配
        assertTrue(processor.matches("AZ"));
        assertTrue(processor.matches("AbcZ"));
        assertFalse(processor.matches("BZ"));
        assertFalse(processor.matches("ABC"));

        // 测试异步匹配
        assertTrue(processor.matchesAsync("AZ").get());
        assertTrue(processor.matchesAsync("AbcZ").get());
        assertFalse(processor.matchesAsync("BZ").get());
        assertFalse(processor.matchesAsync("ABC").get());

        // 关闭处理器
        processor.close();
    }

    @Test
    public void testStartWithIncludeRule() throws IOException, ExecutionException, InterruptedException {
        // 生成JSON配置文件
        String json = """
                {
                  "match_rules": {
                    "include": {
                      "start_with": ["Hello", "Hi"]
                    }
                  }
                }
                """;
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));

        // 加载配置并创建处理器
        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        // 测试匹配
        assertTrue(processor.matches("Hello World"));
        assertTrue(processor.matches("Hi there"));
        assertFalse(processor.matches("Good morning"));

        // 测试异步匹配
        assertTrue(processor.matchesAsync("Hello World").get());
        assertTrue(processor.matchesAsync("Hi there").get());
        assertFalse(processor.matchesAsync("Good morning").get());

        processor.close();
    }

    @Test
    public void testEndWithIncludeRule() throws IOException, ExecutionException, InterruptedException {
        // 生成JSON配置文件
        String json = """
                {
                  "match_rules": {
                    "include": {
                      "end_with": ["World", "Earth"]
                    }
                  }
                }
                """;
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));

        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        assertTrue(processor.matches("Hello World"));
        assertTrue(processor.matches("Beautiful Earth"));
        assertFalse(processor.matches("Hello Universe"));

        assertTrue(processor.matchesAsync("Hello World").get());
        assertTrue(processor.matchesAsync("Beautiful Earth").get());
        assertFalse(processor.matchesAsync("Hello Universe").get());

        processor.close();
    }

    @Test
    public void testEqIncludeRule() throws IOException, ExecutionException, InterruptedException {
        // 生成JSON配置文件
        String json = """
                {
                  "match_rules": {
                    "include": {
                      "eq": ["Exact", "Match"]
                    }
                  }
                }
                """;
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));

        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        assertTrue(processor.matches("Exact"));
        assertTrue(processor.matches("Match"));
        assertFalse(processor.matches("exact"));
        assertFalse(processor.matches("Exactness"));

        assertTrue(processor.matchesAsync("Exact").get());
        assertTrue(processor.matchesAsync("Match").get());
        assertFalse(processor.matchesAsync("exact").get());
        assertFalse(processor.matchesAsync("Exactness").get());

        processor.close();
    }

    @Test
    public void testEqIgnoreCaseIncludeRule() throws IOException, ExecutionException, InterruptedException {
        // 生成JSON配置文件
        String json = """
                {
                  "match_rules": {
                    "include": {
                      "eq_ignore_case": ["Ignore", "Case"]
                    }
                  }
                }
                """;
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));

        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        assertTrue(processor.matches("Ignore"));
        assertTrue(processor.matches("ignore"));
        assertTrue(processor.matches("CASE"));
        assertFalse(processor.matches("Ignored"));

        assertTrue(processor.matchesAsync("Ignore").get());
        assertTrue(processor.matchesAsync("ignore").get());
        assertTrue(processor.matchesAsync("CASE").get());
        assertFalse(processor.matchesAsync("Ignored").get());

        processor.close();
    }

    @Test
    public void testContainsIncludeRule() throws IOException, ExecutionException, InterruptedException {
        // 生成JSON配置文件
        String json = """
                {
                  "match_rules": {
                    "include": {
                      "contains": ["java", "python"]
                    }
                  }
                }
                """;
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));

        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        assertTrue(processor.matches("I love java programming"));
        assertFalse(processor.matches("Python is great"));
        assertFalse(processor.matches("I code in C++"));

        assertTrue(processor.matchesAsync("I love java programming").get());
        assertFalse(processor.matchesAsync("Python is great").get());
        assertFalse(processor.matchesAsync("I code in C++").get());

        processor.close();
    }

    @Test
    public void testExcludeRules() throws IOException, ExecutionException, InterruptedException {
        // 生成JSON配置文件
        String json = """
                {
                  "match_rules": {
                    "include": {
                      "start_with": ["programming"]
                    },
                    "exclude": {
                      "contains": ["java"]
                    }
                  }
                }
                """;
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));

        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        // 以programming开头但不包含java的应该匹配
        assertTrue(processor.matches("programming in python"));
        // 以programming开头但包含java的不应该匹配
        assertFalse(processor.matches("programming in java"));
        // 不以programming开头的不应该匹配
        assertFalse(processor.matches("I love programming"));

        assertTrue(processor.matchesAsync("programming in python").get());
        assertFalse(processor.matchesAsync("programming in java").get());
        assertFalse(processor.matchesAsync("I love programming").get());

        processor.close();
    }

    @Test
    public void testMultipleRuleTypes() throws IOException, ExecutionException, InterruptedException {
        // 生成JSON配置文件
        String json = """
                {
                  "match_rules": {
                    "include": {
                      "start_with": ["Hello"],
                      "end_with": ["World"]
                    }
                  }
                }
                """;
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));

        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        // 以Hello开头或以World结尾的应该匹配
        assertTrue(processor.matches("Hello everyone"));
        assertTrue(processor.matches("Beautiful World"));
        // 不满足任何条件的不应该匹配
        assertFalse(processor.matches("Hi everyone"));

        assertTrue(processor.matchesAsync("Hello everyone").get());
        assertTrue(processor.matchesAsync("Beautiful World").get());
        assertFalse(processor.matchesAsync("Hi everyone").get());

        processor.close();
    }

    @Test
    public void testComplexRuleSet() throws IOException, ExecutionException, InterruptedException {
        // 创建一个复杂的规则集，包含多种规则类型和包含/排除
        String json = """
                {
                  "match_rules": {
                    "include": {
                      "regex": ["^(dev|prod)-.+"],
                      "contains": ["service", "api"]
                    },
                    "exclude": {
                      "end_with": [".bak", ".tmp"],
                      "eq_ignore_case": ["test", "debug"]
                    }
                  }
                }
                """;
        
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));
        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        // 测试复杂规则的匹配情况
        // 满足包含规则且不满足排除规则的应该匹配
        assertTrue(processor.matches("dev-user-service"));
        assertTrue(processor.matches("prod-payment-api"));
        
        // 不满足包含规则的不应该匹配
        assertTrue(processor.matches("stage-payment-api"));
        assertTrue(processor.matches("dev-payment"));
        
        // 满足排除规则的不应该匹配
        assertFalse(processor.matches("dev-service.bak"));
        assertFalse(processor.matches("test"));
        assertFalse(processor.matches("DEBUG"));

        assertTrue(processor.matchesAsync("dev-user-service").get());
        assertTrue(processor.matchesAsync("prod-payment-api").get());

        assertTrue(processor.matchesAsync("stage-payment-api").get());
        assertTrue(processor.matchesAsync("dev-payment").get());

        assertFalse(processor.matchesAsync("dev-service.bak").get());
        assertFalse(processor.matchesAsync("test").get());
        assertFalse(processor.matchesAsync("DEBUG").get());

        processor.close();
    }

    @Test
    public void testEmptyRuleGroup() throws IOException, ExecutionException, InterruptedException {
        // 测试没有包含规则的情况
        String json = """
                {
                  "match_rules": {
                  }
                }
                """;
        
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));
        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        // 不满足排除规则的应该匹配（因为没有包含规则）
        assertTrue(processor.matches("production"));
        assertTrue(processor.matches("test environment"));
        assertTrue(processor.matchesAsync("production").get());
        assertTrue(processor.matchesAsync("test environment").get());

        processor.close();
    }

    @Test
    public void testGetMatchReason() throws IOException, ExecutionException, InterruptedException {
        String json = """
                {
                  "match_rules": {
                    "include": {
                      "start_with": ["Hello"],
                      "contains": ["World"]
                    },
                    "exclude": {
                      "contains": ["test"]
                    }
                  }
                }
                """;
        
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));
        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        // 测试匹配原因
        String text1 = "Hello World";
        String text2 = "Hello test";
        String text3 = "Hi there";
        
        assertTrue(processor.matches(text1));
        assertFalse(processor.matches(text2));
        assertFalse(processor.matches(text3));

        assertTrue(processor.matchesAsync(text1).get());
        assertFalse(processor.matchesAsync(text2).get());
        assertFalse(processor.matchesAsync(text3).get());

        String reason1 = processor.getMatchReason(text1);
        String reason2 = processor.getMatchReason(text2);
        String reason3 = processor.getMatchReason(text3);
        
        assertTrue(reason1.contains("包含"));
        assertTrue(reason2.contains("排除"));
        assertTrue(reason3.contains("不匹配"));

        processor.close();
    }

    @Test
    public void testNullInput() throws IOException, ExecutionException, InterruptedException {
        String json = """
                {
                  "match_rules": {
                    "include": {
                      "contains": ["test"]
                    }
                  }
                }
                """;
        
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));
        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        // 测试null输入
        assertFalse(processor.matches(null));
        assertEquals("输入为null", processor.getMatchReason(null));

        assertFalse(processor.matchesAsync(null).get());

        processor.close();
    }

    @Test
    public void testStartWithIgnoreCaseRule() throws IOException, ExecutionException, InterruptedException {
        MatchRulesConfigBean config = new MatchRulesConfigBean();
        MatchRules rules = new MatchRules();
        RuleGroup includeGroup = new RuleGroup();
        includeGroup.setStartWithIgnoreCase(Arrays.asList("Java", "Python"));
        rules.setInclude(includeGroup);
        config.setMatchRules(rules);

        // 生成JSON配置文件
        String json = """
            {
              "match_rules": {
                "include": {
                  "start_with_ignore_case": ["Java", "Python"]
                }
              }
            }
            """;
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));

        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        // 测试匹配
        assertTrue(processor.matches("Java programming"));
        assertTrue(processor.matches("java programming"));
        assertTrue(processor.matches("JAVA PROGRAMMING"));
        assertTrue(processor.matches("Python code"));
        assertTrue(processor.matches("python code"));
        assertFalse(processor.matches("C++ programming"));

        // 测试异步匹配
        assertTrue(processor.matchesAsync("Java programming").get());
        assertTrue(processor.matchesAsync("java programming").get());
        assertTrue(processor.matchesAsync("JAVA PROGRAMMING").get());
        assertTrue(processor.matchesAsync("Python code").get());
        assertTrue(processor.matchesAsync("python code").get());
        assertFalse(processor.matchesAsync("C++ programming").get());

        processor.close();
    }

    @Test
    public void testEndWithIgnoreCaseRule() throws IOException, ExecutionException, InterruptedException {
        MatchRulesConfigBean config = new MatchRulesConfigBean();
        MatchRules rules = new MatchRules();
        RuleGroup includeGroup = new RuleGroup();
        includeGroup.setEndWithIgnoreCase(Arrays.asList(".java", ".py"));
        rules.setInclude(includeGroup);
        config.setMatchRules(rules);

        // 生成JSON配置文件
        String json = """
            {
              "match_rules": {
                "include": {
                  "end_with_ignore_case": [".java", ".py"]
                }
              }
            }
            """;
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));

        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        // 测试匹配
        assertTrue(processor.matches("Main.java"));
        assertTrue(processor.matches("Main.JAVA"));
        assertTrue(processor.matches("script.py"));
        assertTrue(processor.matches("script.PY"));
        assertFalse(processor.matches("Main.class"));

        // 测试异步匹配
        assertTrue(processor.matchesAsync("Main.java").get());
        assertTrue(processor.matchesAsync("Main.JAVA").get());
        assertTrue(processor.matchesAsync("script.py").get());
        assertTrue(processor.matchesAsync("script.PY").get());
        assertFalse(processor.matchesAsync("Main.class").get());

        processor.close();
    }

    @Test
    public void testContainsIgnoreCaseRule() throws IOException, ExecutionException, InterruptedException {
        MatchRulesConfigBean config = new MatchRulesConfigBean();
        MatchRules rules = new MatchRules();
        RuleGroup includeGroup = new RuleGroup();
        includeGroup.setContainsIgnoreCase(Arrays.asList("Java", "Python"));
        rules.setInclude(includeGroup);
        config.setMatchRules(rules);

        // 生成JSON配置文件
        String json = """
            {
              "match_rules": {
                "include": {
                  "contains_ignore_case": ["Java", "Python"]
                }
              }
            }
            """;
        objectMapper.writeValue(tempRuleFile, objectMapper.readTree(json));

        MatchRulesConfigBean loadedConfig = objectMapper.readValue(tempRuleFile, MatchRulesConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(loadedConfig);

        // 测试匹配
        assertTrue(processor.matches("I love Java programming"));
        assertTrue(processor.matches("I love JAVA programming"));
        assertTrue(processor.matches("I use python for data science"));
        assertTrue(processor.matches("I use PYTHON for data science"));
        assertFalse(processor.matches("I code in C++"));

        // 测试异步匹配
        assertTrue(processor.matchesAsync("I love Java programming").get());
        assertTrue(processor.matchesAsync("I love JAVA programming").get());
        assertTrue(processor.matchesAsync("I use python for data science").get());
        assertTrue(processor.matchesAsync("I use PYTHON for data science").get());
        assertFalse(processor.matchesAsync("I code in C++").get());

        processor.close();
    }

    @Test
    public void testRegexRule() throws IOException, ExecutionException, InterruptedException {
        TranslationRuleConfigBean translationRuleConfigBean = objectMapper.readValue(Files.newInputStream(Path.of("../半汉化匹配规则.json")), TranslationRuleConfigBean.class);
        ParallelMatchRuleProcessor processor = new ParallelMatchRuleProcessor(translationRuleConfigBean.getOriginal());
        assertNotNull(translationRuleConfigBean);
        String text = "对 ~mission(TargetName) （低风险目标 LRT）的悬赏已发出";
        System.out.println(processor.getMatchReason(text));
        assertFalse(processor.matches(text));
        String reg = ".*~mission\\([^)]+\\).*";
        System.out.println(text.matches(reg));
    }
}