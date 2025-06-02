package cn.citizenwiki.utils;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class PinYinUtil {

    private static final Logger logger = LoggerFactory.getLogger(PinYinUtil.class);

    // 创建 Pattern 对象
    private static final Pattern pattern = Pattern.compile("[\u4e00-\u9fa5]");

    // 将提取的汉字字符串转换为拼音
    public static String getPinyin(String hanziStr) {
        if (hanziStr == null || hanziStr.isEmpty()) {
            return hanziStr;
        }

        StringBuilder result = new StringBuilder();
        StringBuilder chinese = new StringBuilder();

        for (int i = 0; i < hanziStr.length(); i++) {
            char c = hanziStr.charAt(i);
            if (pattern.matcher(String.valueOf(c)).matches()) {
                // 是汉字，添加到临时缓冲区
                chinese.append(c);
            } else {
                // 不是汉字，先处理之前收集的汉字
                if (!chinese.isEmpty()) {
                    result.append(PinyinHelper.toPinyin(chinese.toString(), PinyinStyleEnum.FIRST_LETTER, ""));
                    chinese.setLength(0);
                }
                // 直接添加非汉字字符
                result.append(c);
            }
        }

        // 处理最后剩余的汉字
        if (!chinese.isEmpty()) {
            result.append(PinyinHelper.toPinyin(chinese.toString(), PinyinStyleEnum.FIRST_LETTER, ""));
        }

        return result.toString();
    }

}
