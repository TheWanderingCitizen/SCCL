package cn.citizenwiki.utils;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PinYinUtil {

    private static final Logger logger = LoggerFactory.getLogger(PinYinUtil.class);

    // 将提取的汉字字符串转换为拼音
    public static String getPinyin(String hanziStr) {
        String pinyinStr = "";
        // 获取该汉字的拼音数组
        try {
            pinyinStr = PinyinHelper.toPinyin(hanziStr, PinyinStyleEnum.FIRST_LETTER,"");
        } catch (Exception e) {
            // 处理无法转换的字符
            logger.error("[{}]转拼音异常", hanziStr, e);
        }
        return pinyinStr;  // 返回拼音的完整拼音字符串
    }
}
