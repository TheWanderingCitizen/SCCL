package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import cn.citizenwiki.utils.SearchableLocationReplacer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 全汉化处理器
 */
public class FullTranslationProcessor extends CommonTranslationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FullTranslationProcessor.class);

    private SearchableLocationReplacer searchableLocationReplacer;

    // 定义规则

    public FullTranslationProcessor() {
        super(GithubConfig.FULL_BRANCH_NAME);
    }

    @Override
    public void beforeProcess(Map<String, PZTranslation> mergedTranslateMap) {
        super.beforeProcess(mergedTranslateMap);
        searchableLocationReplacer = new SearchableLocationReplacer(mergedTranslateMap);
    }

    /**
     * 如果是ptu版本则cdn上传到ptu目录，否则上传到full目录
     *
     * @param lastFileVersion 最新版本号
     * @return
     */
    @Override
    protected String getBucketPath(FileVersion lastFileVersion) {
        if (FileVersion.Profile.PTU.name().equals(lastFileVersion.getProfile())) {
            logger.info("最新版本为PTU，全汉化版本将上传至CDN的[{}]目录", S3Config.PTU_DIR);
            return S3Config.PTU_DIR + "/global.ini";
        } else {
            return S3Config.FULL_DIR + "/global.ini";
        }
    }

    @Override
    public void processBw(PZTranslation pzTranslation, BufferedWriter bw) {
        //写入文件
        if (bw != null) {
            String translation = searchableLocationReplacer.replace(pzTranslation);
            try {
                bw.write(pzTranslation.getKey() + "=" + translation);
                bw.newLine(); // 写入换行符
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected boolean shouldPublish(FileVersion lastFileVersion) {
        return true;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public String getProcessorName() {
        return "汉化处理器";
    }

}
