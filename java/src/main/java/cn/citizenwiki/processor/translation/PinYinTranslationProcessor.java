package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.config.GlobalConfig;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import cn.citizenwiki.utils.PinYinUtil;
import cn.citizenwiki.utils.SearchableLocationReplacer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * 拼音汉化处理器
 */
public class PinYinTranslationProcessor extends CommonTranslationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PinYinTranslationProcessor.class);

    // 定义规则

    public PinYinTranslationProcessor() {
        super(GithubConfig.PINYIN_BRANCH_NAME);
    }

    @Override
    public void beforeProcess(Map<String, PZTranslation> mergedTranslateMap) {
        super.beforeProcess(mergedTranslateMap);
    }

    /**
     * 如果是ptu版本则cdn上传到ptu目录，否则上传到full目录
     *
     * @param lastFileVersion 最新版本号
     * @return
     */
    @Override
    protected String getBucketPath(FileVersion lastFileVersion) {
        return S3Config.PINYIN_DIR + "/global.ini";
    }

    @Override
    public void processBw(PZTranslation pzTranslation, BufferedWriter bw) {
        //写入文件
        if (bw != null) {
            String translation = pzTranslation.getTranslation();
            String key = pzTranslation.getKey();
            String keyLowerCase = key.toLowerCase();
            if (keyLowerCase.startsWith("item_name")
                    || keyLowerCase.startsWith("item_decoration")
                    || SearchableLocationReplacer.isLocationKey(key)) {
                String pinyin = PinYinUtil.getPinyin(translation);
                if (Objects.nonNull(pinyin)) {
                    translation = translation + "[" + PinYinUtil.getPinyin(translation) + "]";
                }
            }
            try {
                bw.write(String.join("=", key, translation));
                if (!translation.endsWith("\r") && !translation.endsWith("\n")) {
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
//            logger.info("推送通道为[{}]，不发布拼音版本", GlobalConfig.SW_PROFILE.name());
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
        return "拼音汉化处理器";
    }

}
