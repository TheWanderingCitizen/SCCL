package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

/**
 * 全汉化处理器
 */
public class FullTranslationProcessor extends CommonTranslationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FullTranslationProcessor.class);

    /**
     * 字典序倒序排列的map
     * <地点译文名，地点英文名>
     * 在替换时，为防止“派罗V”被替换成“派罗[Pyro]V[Pyro V]”，所以需要按照字典序倒序先替换“派罗V”，
     * 这样在替换词检索到“派罗”时，判断已替换关键词中是否包含当前关键词，即可避免上述情况
     */
    private static final Map<String, String> localtionMap = new TreeMap<>(Collections.reverseOrder());

    // 定义规则

    public FullTranslationProcessor() {
        super(GithubConfig.FULL_BRANCH_NAME);
    }

    @Override
    public void beforeProcess(Map<String, PZTranslation> mergedTranslateMap) {
        super.beforeProcess(mergedTranslateMap);
        //找到所有地点封装到Map
        for (Map.Entry<String, PZTranslation> entry : mergedTranslateMap.entrySet()) {
            String key = entry.getKey();
            if (isLocationKey(key)) {
                localtionMap.put(entry.getValue().getTranslation(), entry.getValue().getOriginal());
            }
        }
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
            String translation = pzTranslation.getTranslation();
            if (pzTranslation.getKey().startsWith("mission_location")){
                //将任务地名中的译名替换为译名[原文]，比如“哈哈斯坦顿”→“哈哈斯坦顿[Stanton]”
                Set<String> replacedWords = new HashSet<>();
                for (Map.Entry<String, String> entry : localtionMap.entrySet()) {
                    if (translation.contains(entry.getKey()) && !isReplacedWords(replacedWords, entry.getKey())) {
                        translation = translation.replace(entry.getKey(), entry.getKey() + "["+entry.getValue()+"]");
                        replacedWords.add(entry.getKey());
                    }
                }
                logger.debug("key：[{}]中的地点已被替换，替换后译文：[{}]", pzTranslation.getKey(), translation);
            }
            try {
                bw.write(pzTranslation.getKey() + "=" + translation);
                bw.newLine(); // 写入换行符
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 当前关键词是否存在于已替换的关键词中，防止出现重复替换
     * @param replacedWords
     * @param key
     * @return
     */
    private boolean isReplacedWords(Set<String> replacedWords, String key) {
        for (String replacedWord : replacedWords) {
            if (replacedWord.contains(key)) {
                return true;
            }
        }
        return false;
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

    /**
     * 判断key是否为标准地名
     *
     * @param key
     * @return
     */
    private static boolean isLocationKey(String key) {
        return (key.startsWith("Pyro") && !key.contains("_desc") && !key.contains("_add") && !key.contains("drlct"))
                || (key.startsWith("Stanton") && !key.contains("_desc") && !key.contains("_add"));
    }
}
