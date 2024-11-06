package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.PZTranslation;
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
        if (!value.contains("[")) {
            // 使用正则表达式
            boolean matches = Pattern.matches("^item_Name.*S\\d{2}.*$", key) || Pattern.matches("^item.*S\\d.*$", key);
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
                bw.newLine(); // 写入换行符
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected boolean shouldPublish(FileVersion lastFileVersion) {
        if (!FileVersion.Profile.PU.name().equals(lastFileVersion.getProfile())){
            logger.info("最新版本为[{}]，不发布双语版本", lastFileVersion.getProfile());
            return false;
        }
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
