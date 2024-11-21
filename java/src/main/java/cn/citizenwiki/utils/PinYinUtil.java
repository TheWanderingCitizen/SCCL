package cn.citizenwiki.utils;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.multipinyin.MultiPinyinConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PinYinUtil {

    private static final Logger logger = LoggerFactory.getLogger(PinYinUtil.class);

    // 缓存正则表达式 Pattern
    private static final Pattern HANZI_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");

    public static final String  SEPARETOR = " ";

    // 设置拼音输出格式
    private static final  HanyuPinyinOutputFormat format;

    static {
        MultiPinyinConfig.multiPinyinPath = Paths.get("my_multi_pinyin.txt").toAbsolutePath().toString();
        format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.UPPERCASE);  // 输出大写拼音
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);  // 不带声调
        format.setVCharType(HanyuPinyinVCharType.WITH_V);  // 使用 'v' 表示 'ü'

    }

    // 将字符串中的所有汉字提取出来并转换为拼音
    public static String convertToPinyin(String input) {
        StringBuilder hanziBuilder = new StringBuilder();


        Matcher matcher = HANZI_PATTERN.matcher(input);

        // 提取所有汉字并组成一个新的字符串
        while (matcher.find()) {
            hanziBuilder.append(matcher.group());  // 将汉字加入到新字符串
        }

        String hanziStr = hanziBuilder.toString();  // 新字符串，仅包含汉字

        // 将提取出的汉字转换为拼音
        return getPinyin(hanziStr);
    }

    // 将提取的汉字字符串转换为拼音
    public static String getPinyin(String hanziStr) {
        String pinyinStr = "";
        // 获取该汉字的拼音数组
        try {
            pinyinStr = PinyinHelper.toHanYuPinyinString(hanziStr, format, SEPARETOR, false);
        } catch (Exception e) {
            // 处理无法转换的字符
            logger.error("[{}]转拼音异常", hanziStr, e);
        }

        return pinyinStr;  // 返回拼音的完整拼音字符串
    }
}
