package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.PZTranslation;
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
    private final List<String> startWithWords = Arrays.stream(new String[]{"item_Name", "vehicle_Name", "Pyro_JumpPoint_", "Stanton", "Terra_JumpPoint",
            "stanton2", "Pyro", "mission_location", "mission_Item", "mission_client", "items_"}).map(String::toLowerCase).toList();

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
                bw.newLine(); // 写入换行符
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected boolean shouldPublish(FileVersion lastFileVersion) {
        if (!FileVersion.Profile.PU.name().equals(lastFileVersion.getProfile())){
            logger.info("最新版本为[{}]，不发布半汉化版本", lastFileVersion.getProfile());
            return false;
        }
        return true;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    /**
     * 需要被处理返回true，否则false
     *
     * @param PZTranslation
     * @return
     */
    public boolean needProcess(PZTranslation PZTranslation) {
        String keyLower = PZTranslation.getKey().toLowerCase();
        return ((startWithWords.stream().anyMatch(keyLower::startsWith)
                || keyLower.contains("_repui")
                || keyLower.endsWith("_from"))
                && !keyLower.contains("desc"));
    }

    @Override
    public String getProcessorName() {
        return "半汉化处理器";
    }
}