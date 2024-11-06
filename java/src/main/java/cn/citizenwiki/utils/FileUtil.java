package cn.citizenwiki.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class FileUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

    public static void deleteDirectory(String dir) throws IOException {
        Path directory = Path.of(dir);
        if (Files.exists(directory)) {
            // 深度优先遍历目录并删除每个文件和子目录
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder()) // 先删除子文件和子目录
                    .forEach(file -> {
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            logger.info("目录[{}]删除成功", directory);
        }
    }

}
