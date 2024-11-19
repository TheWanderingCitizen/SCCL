package cn.citizenwiki.processor.translation;

import cn.citizenwiki.MergeAndConvert;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 处理器Task类，会在主流程中被调用
 * @see MergeAndConvert#fetchAndMergeTranslations()
 */
public class ProcessorTask implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ProcessorTask.class);

    //汉化处理器
    private final TranslationProcessor translationProcessor;
    //paratranz汉化文件合并后的内容
    private final Map<String, PZTranslation> mergedTranslateMap;
    //最新版本号
    private final FileVersion lastFileVersion;

    public ProcessorTask(TranslationProcessor processor, Map<String, PZTranslation> mergedTranslateMap, FileVersion lastFileVersion) {
        this.translationProcessor = processor;
        this.mergedTranslateMap = mergedTranslateMap;
        this.lastFileVersion = lastFileVersion;
    }

    @Override
    public void run() {
        logger.info("[{}]开始执行", translationProcessor.getProcessorName());
        try {
            //处理前
            translationProcessor.beforeProcess(mergedTranslateMap);
            //处理汉化文本
            for (Map.Entry<String, PZTranslation> entry : mergedTranslateMap.entrySet()) {
                PZTranslation cloneValue = entry.getValue().clone();
                translationProcessor.process(cloneValue);
            }
            //处理后
            translationProcessor.afterProcess(lastFileVersion);
            logger.info("[{}]执行完成", translationProcessor.getProcessorName());
        } catch (Exception e) {
            logger.error("[{}]执行异常", translationProcessor.getProcessorName(), e);
        }

    }
}