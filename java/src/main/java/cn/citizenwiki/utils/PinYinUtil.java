package cn.citizenwiki.utils;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PinYinUtil {

    private static final Logger logger = LoggerFactory.getLogger(PinYinUtil.class);

    // 创建 Pattern 对象
    private static final Pattern pattern = Pattern.compile("[\u4e00-\u9fa5]");

    // 将提取的汉字字符串转换为拼音
    public static String getPinyin(String hanziStr) {
        Matcher matcher = pattern.matcher(hanziStr);

        // 创建一个 StringBuilder 用于保存所有匹配的汉字
        StringBuilder chineseChars = new StringBuilder();

        // 查找匹配的汉字并添加到 StringBuilder 中
        while (matcher.find()) {
            chineseChars.append(matcher.group());
        }
        if (!chineseChars.isEmpty()) {
            String pinyinStr = "";
            // 获取该汉字的拼音数组
            try {
                pinyinStr = PinyinHelper.toPinyin(chineseChars.toString(), PinyinStyleEnum.FIRST_LETTER,"");
            } catch (Exception e) {
                // 处理无法转换的字符
                logger.error("[{}]转拼音异常", hanziStr, e);
            }
            return pinyinStr;  // 返回拼音的完整拼音字符串
        }
       return null;
    }
}
