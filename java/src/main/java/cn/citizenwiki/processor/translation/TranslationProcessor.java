package cn.citizenwiki.processor.translation;

import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import cn.citizenwiki.processor.BaseProcessor;

import java.util.Map;

/**
 * 翻译处理器接口
 */
public interface TranslationProcessor extends BaseProcessor {

    /**
     * 在所有词条开始处理前调用，只会调用一次
     */
    void beforeProcess(Map<String, PZTranslation> mergedTranslateMap);

    /**
     * 处理词条,每遍历到一个词条都会调用一次
     * @param PZTranslation 词条对象
     */
    void process(PZTranslation PZTranslation);

    /**
     * 在所有词条开始处理后调用，只会调用一次
     */
    void afterProcess(FileVersion lastFileVersion);



}
