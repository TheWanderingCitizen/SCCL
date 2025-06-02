package cn.citizenwiki.config;

import cn.citizenwiki.model.config.MatchRulesConfigBean;
import cn.citizenwiki.model.config.TranslationRuleConfigBean;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.utils.YamlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GlobalConfig {

    //所有生成文件的输出目录
    public static final String OUTPUT_DIR = "final_output";
    //推送（push仓库，pr，cdn）的总开关，默认关闭
    public static final boolean SW_PUBLISH;
    //推送版本开关，默认关闭
    public static final FileVersion.Profile SW_PROFILE;
    private static final Logger logger = LoggerFactory.getLogger(GlobalConfig.class);

    static {
        String swPublish = System.getenv("SW_PUBLISH");
        if (Objects.nonNull(swPublish) && !swPublish.isBlank()) {
            SW_PUBLISH = Boolean.parseBoolean(swPublish);
        } else {
            SW_PUBLISH = false;
        }
        logger.info("推送开关：[{}]", SW_PUBLISH ? "开启" : "关闭");
        String swProfile = System.getenv("SW_PROFILE");
        if (Objects.nonNull(swProfile) && !swProfile.isBlank()) {
            SW_PROFILE = FileVersion.Profile.valueOf(swProfile);
        } else {
            SW_PROFILE = FileVersion.Profile.PTU;
        }
        logger.info("推送通道：[{}]", SW_PROFILE.name());
    }

    public static class MatcherRulesConfig {
        private static final Map<String, TranslationRuleConfigBean> TRANSLATION_RULE_CONFIGS;
        private static final Map<String, MatchRulesConfigBean> MATCH_RULES_CONFIGS;

        static {
            Path baseDir = Path.of("规则配置");
            try (Stream<Path> configDir = Files.list(baseDir)) {
                // 加载 TranslationRuleConfigBean 格式的配置
                TRANSLATION_RULE_CONFIGS = configDir
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".yaml"))
                        .collect(Collectors.toUnmodifiableMap(
                                path -> baseDir.relativize(path).normalize().toString().replace('\\', '/'),
                                path -> {
                                    try {
                                        return YamlUtil.readObjFromYaml(path, TranslationRuleConfigBean.class);
                                    } catch (IOException e) {
                                        logger.debug("无法加载为 TranslationRuleConfigBean 格式：{}", path);
                                        return null;
                                    }
                                }
                        ));
            } catch (IOException e) {
                throw new RuntimeException("读取【规则配置】目录失败", e);
            }
            // 加载 MatchRulesConfigBean 格式的配置（在"规则配置/可导入规则"目录下）
            Path matchRulesBaseDir = Path.of("规则配置/规则");
            try (Stream<Path> configDir = Files.walk(matchRulesBaseDir)) {
                MATCH_RULES_CONFIGS = configDir
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".yaml"))
                        .collect(Collectors.toUnmodifiableMap(
                                path -> baseDir.relativize(path).normalize().toString().replace('\\', '/'),
                                path -> {
                                    try {
                                        return YamlUtil.readObjFromYaml(path, MatchRulesConfigBean.class);
                                    } catch (IOException e) {
                                        String msg = "无法加载"+path;
                                        logger.debug( msg);
                                        throw new RuntimeException(msg,e);
                                    }
                                }
                        ));
            } catch (IOException e) {
                throw new RuntimeException("读取【规则配置/规则】目录失败", e);
            }
        }

        /**
         * 根据文件名获取翻译规则配置
         */
        public static TranslationRuleConfigBean getTranslationRuleConfig(String fileName) {
            return TRANSLATION_RULE_CONFIGS.get(fileName);
        }

        /**
         * 根据文件名获取匹配规则配置（用于导入）
         */
        public static MatchRulesConfigBean getMatchRulesConfig(String fileName) {
            return MATCH_RULES_CONFIGS.get(fileName);
        }

        /**
         * 兼容性方法：优先返回 TranslationRuleConfigBean
         */
        public static TranslationRuleConfigBean getMatcherRule(String fileName) {
            TranslationRuleConfigBean config = getTranslationRuleConfig(fileName);
            if (config != null) {
                return config;
            }
            return null;
        }

        /**
         * 获取所有翻译规则配置
         */
        public static Map<String, TranslationRuleConfigBean> getAllTranslationRuleConfigs() {
            return TRANSLATION_RULE_CONFIGS;
        }

        /**
         * 获取所有匹配规则配置
         */
        public static Map<String, MatchRulesConfigBean> getAllMatchRulesConfigs() {
            return MATCH_RULES_CONFIGS;
        }


    }
}
