package cn.citizenwiki.utils;

/**
 * paratranz文件版本工具类
 */
public class ParatranzFileUtil {

    public static boolean isFormatedName(String name) {
        String formatName = getRealFileName(name);
        return formatName.endsWith(".json") && formatName.split(" ").length == 3 && !formatName.contains("-");
    }

    public static String getRealFileName(String name) {
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        return name;
    }

}
