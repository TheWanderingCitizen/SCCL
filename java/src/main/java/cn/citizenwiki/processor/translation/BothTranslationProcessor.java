package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.config.GlobalConfig;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import cn.citizenwiki.utils.SearchableLocationReplacer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 双语汉化处理器
 *
 */
public class BothTranslationProcessor extends CommonTranslationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BothTranslationProcessor.class);

    private static final Pattern itemPattern1 = Pattern.compile("^item_Name.*S\\d{2}.*$");
    private static final Pattern itemPattern2 = Pattern.compile("^item.*S\\d.*$");

    // 定义规则

    public BothTranslationProcessor() {
        super(GithubConfig.DUAL_BRANCH_NAME);
    }

    @Override
    protected String getBucketPath(FileVersion lastFileVersion) {
        return S3Config.BOTH_DIR + "/global.ini";
    }

    @Override
    public void processBw(PZTranslation pzTranslation, BufferedWriter bw) {
        String key = pzTranslation.getKey();
        String value = pzTranslation.getTranslation();
        if (HalfTranslationProcessor.needProcess(pzTranslation) && !value.contains("[")) {
            // 使用正则表达式
            boolean matches = itemPattern1.matcher(key).matches() || itemPattern2.matcher(key).matches() || SearchableLocationReplacer.isLocationKey(key);
            if ((key.contains("Stanton") && key.contains("_")) || key.contains("mission_location") || key.contains("mission_contractor") || matches) {
                value = pzTranslation.getOriginal() + " [" + pzTranslation.getTranslation() + "]";
            } else {
                value = pzTranslation.getOriginal() + "\\n" + pzTranslation.getTranslation();
            }
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
//        if (!FileVersion.Profile.LIVE.equals(GlobalConfig.SW_PROFILE)) {
//            logger.info("推送通道为[{}]，不发布双语版本", GlobalConfig.SW_PROFILE.name());
//            return false;
//        }
        return true;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public String getProcessorName() {
        return "双语汉化处理器";
    }
}
