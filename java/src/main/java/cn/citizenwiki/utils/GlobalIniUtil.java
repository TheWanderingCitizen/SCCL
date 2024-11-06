package cn.citizenwiki.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

public class GlobalIniUtil {

    private static final Logger logger = LoggerFactory.getLogger(GlobalIniUtil.class);

    public static LinkedHashMap<String, String> convertIniToMap(InputStream inputStream) {
        String iniContent;
        try {
            iniContent = latin1ToUft8String(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 用于存储键值对的 Map
        LinkedHashMap<String, String> iniMap = new LinkedHashMap<>();

        // 按行解析文件内容
        String[] lines = iniContent.split("\n");
        for (String line : lines) {
            // 检查是否包含键值对
            if (line.contains("=")) {
                String[] keyValue = line.split("=", 2); // 分割成键和值
                String key = keyValue[0];
                String value = keyValue[1];
                iniMap.put(key, value);
            }
        }

        return iniMap;
    }


    /**
     * 将latin1输入流转换为UTF8字节数组,替换latin1的无空格字符，并跳过BOM
     * @param inputStream latin1文本输入流
     * @return uft8字符串
     * @throws IOException
     */
    private static String latin1ToUft8String(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; // 使用4KB的缓冲区
            int bytesRead;
            boolean bomSkipped = false; // 标记是否已跳过BOM

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // 仅在第一次循环时检查BOM
                if (!bomSkipped && bytesRead >= 3 &&
                        buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB && buffer[2] == (byte) 0xBF) {
                    // 跳过BOM
                    bomSkipped = true;
                    // 处理从第四个字节开始的内容
                    bytesRead -= 3; // 调整bytesRead
                    byteArrayOutputStream.write(buffer, 3, bytesRead); // 直接写入跳过BOM后的数据
                    continue; // 跳过本次循环
                }

                // 处理无空格字符 (0xA0) 为 UTF-8 的无空格字符 (0xC2 0xA0)
                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == (byte) 0xA0) {
                        byteArrayOutputStream.write(0xC2); // 写入UTF-8前导字节
                        byteArrayOutputStream.write(0xA0); // 写入UTF-8无空格字符
                    } else {
                        byteArrayOutputStream.write(buffer[i]); // 写入其他字节
                    }
                }
            }
            return byteArrayOutputStream.toString(StandardCharsets.UTF_8); // 返回字节数组
        }
    }

}
