package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 半汉化处理器
 *
 */
public class HalfTranslationProcessor extends CommonTranslationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HalfTranslationProcessor.class);

    // 定义规则
    private static final List<String> startWithWords = Arrays.stream(new String[]{"item_Name", "vehicle_Name", "Pyro_JumpPoint_", "Stanton", "Terra_JumpPoint",
            "stanton2", "ui_pregame_port", "RR_", "Pyro", "mission_location", "mission_Item", "mission_client", "items_", "dfm_crusader"
            //焰联
            , "AsteroidBase_"
            //行政机库
            , "ExecutiveHangar_"
    }).toList();

    public HalfTranslationProcessor() {
        super(GithubConfig.HALF_BRANCH_NAME);
    }

    @Override
    protected String getBucketPath(FileVersion lastFileVersion) {
        return S3Config.HALF_DIR + "/global.ini";
    }

    @Override
    public void processBw(PZTranslation pzTranslation, BufferedWriter bw) {
        String value = pzTranslation.getTranslation();
        if (needProcess(pzTranslation)) {
            value = pzTranslation.getOriginal();
        }
        //写入文件
        if (bw != null) {
            try {
                bw.write(pzTranslation.getKey() + "=" + value);
                if (!value.endsWith("\r") && !value.endsWith("\n")) {
                    bw.newLine();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected boolean shouldPublish(FileVersion lastFileVersion) {
//        if (!FileVersion.Profile.LIVE.equals(GlobalConfig.SW_PROFILE)){
//            logger.info("推送通道为[{}]，不发布半汉化版本", GlobalConfig.SW_PROFILE.name());
//            return false;
//        }
        return true;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    /**
     * 需要被处理返回true，否则false
     * 该规则被暴露,使之可以被其它processor使用
     * @param pzTranslation
     * @return
     */
    public static boolean needProcess(PZTranslation pzTranslation) {
        String keyLower = pzTranslation.getKey().toLowerCase();
        return ((startWithWords.stream().anyMatch(pzTranslation.getKey()::startsWith) || keyLower.contains("_repui") || keyLower.endsWith("_from"))
                && !keyLower.contains("desc")
                //包含表达式的不能处理
                && (!pzTranslation.getOriginal().contains("~") && !pzTranslation.getOriginal().contains("%")));
    }

    @Override
    public String getProcessorName() {
        return "半汉化处理器";
    }
}
