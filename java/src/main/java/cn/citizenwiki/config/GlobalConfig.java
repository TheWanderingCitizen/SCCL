package cn.citizenwiki.config;

import cn.citizenwiki.model.dto.FileVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class GlobalConfig {

    private static final Logger logger = LoggerFactory.getLogger(GlobalConfig.class);

    //所有生成文件的输出目录
    public static final String OUTPUT_DIR = "final_output";

    //推送（push仓库，pr，cdn）的总开关，默认关闭
    public static final boolean SW_PUBLISH;

    //推送版本开关，默认关闭
    public static final FileVersion.Profile SW_PROFILE;

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
}
