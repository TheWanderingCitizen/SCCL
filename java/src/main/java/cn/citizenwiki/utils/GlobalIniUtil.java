package cn.citizenwiki.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public class GlobalIniUtil {

    public static final byte SPACE_BYTE = (byte) 0xA0;
    public static final byte NO_BREAK_BYTE = (byte) 0xC2;
    private static final Logger logger = LoggerFactory.getLogger(GlobalIniUtil.class);

    public static LinkedHashMap<String, String> convertIniToMap(Path path) {
        String iniContent;
        try {
            iniContent = latin1ToUtf8String(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 用于存储键值对的 Map
        LinkedHashMap<String, String> iniMap = new LinkedHashMap<>();

        // 按行解析文件内容
        BufferedReader bufferedReader = new BufferedReader(new StringReader(iniContent));
        bufferedReader.lines().forEach(line -> {
            // 检查是否包含键值对
            try {
                if (line.contains("=")) {
                    String[] keyValue = line.split("=", 2); // 分割成键和值
                    String key = keyValue[0];
                    String value = keyValue[1];
                    iniMap.put(key, value);
                }
            } catch (Exception e) {
                logger.error(line, e);
            }
        });
        return iniMap;
    }

    /**
     * 跳过bom。并将0XA0替换为no-break space
     *
     * @param filePath Latin1文件的路径
     * @return UTF-8字符串
     * @throws IOException
     */
    private static String latin1ToUtf8String(Path filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096]; // 4KB缓冲区
            boolean previousByteWasC2 = false; // 前一个字节是否为0xC2

            // 读取开头的3个字节，检查BOM
            byte[] bomBuffer = new byte[3];
            int bomBytesRead = inputStream.read(bomBuffer);
            if (bomBytesRead >= 3 &&
                    bomBuffer[0] == (byte) 0xEF &&
                    bomBuffer[1] == (byte) 0xBB &&
                    bomBuffer[2] == (byte) 0xBF) {
            } else if (bomBytesRead > 0) {
                // 如果没有BOM，将读取的字节写入输出流
                byteArrayOutputStream.write(bomBuffer, 0, bomBytesRead);
            }
            // 处理剩余的数据
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // 处理无空格字符（0xA0）并转换为UTF-8
                for (int i = 0; i < bytesRead; i++) {
                    byte currentByte = buffer[i];
                    if (currentByte == SPACE_BYTE && !previousByteWasC2) {
                        // 写入UTF-8无空格字符（0xC2 0xA0）
                        byteArrayOutputStream.write(NO_BREAK_BYTE);
                        byteArrayOutputStream.write(SPACE_BYTE);
                        previousByteWasC2 = false;
                    } else {
                        // 写入其他字节
                        byteArrayOutputStream.write(currentByte);
                        previousByteWasC2 = (currentByte == NO_BREAK_BYTE);
                    }
                }
            }

            // 将字节数组转换为UTF-8字符串
            return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
        }
    }

}
