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
import java.util.Map;

/**
 * 可搜索汉化处理器
 */
public class SearchableTranslationProcessor extends CommonTranslationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SearchableTranslationProcessor.class);

    private SearchableLocationReplacer searchableLocationReplacer;

    // 定义规则

    public SearchableTranslationProcessor() {
        super(GithubConfig.SEARCH_BRANCH_NAME);
    }

    @Override
    public void beforeProcess(Map<String, PZTranslation> mergedTranslateMap, FileVersion lastFileVersion) {
        super.beforeProcess(mergedTranslateMap, lastFileVersion);
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
        return S3Config.SEARCH_DIR + "/global.ini";
    }

    @Override
    public void processBw(PZTranslation pzTranslation, BufferedWriter bw) {
        //写入文件
        if (bw != null) {
            String translation = pzTranslation.getTranslation();
            if(SearchableLocationReplacer.isSearchableKey(pzTranslation.getKey())){
                translation = translation.replace(translation, translation + "[" + pzTranslation.getOriginal() + "]");
            }
            translation = searchableLocationReplacer.replace(pzTranslation.getKey(), translation);
            try {
                bw.write(pzTranslation.getKey() + "=" + translation);
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
        return true;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public String getProcessorName() {
        return "可搜索汉化处理器";
    }

}
