package cn.citizenwiki.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    /**
     * 递归复制源目录及其所有内容到目标目录
     *
     * @param sourceDir 源目录路径
     * @param targetDir 目标目录路径
     * @throws IOException 如果发生I/O异常
     */
    public static void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        // 确保目标目录存在，如果不存在则创建
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        // 使用 Files.walkFileTree 递归遍历源目录，复制每个文件和子目录
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // 计算目标文件路径
                Path targetFile = targetDir.resolve(sourceDir.relativize(file));
                // 创建目标文件所在的目录
                Files.createDirectories(targetFile.getParent());
                // 复制文件
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                // 返回继续遍历，处理子目录
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 将指定文件夹压缩为ZIP文件，压缩包中的路径包含文件夹名称
     *
     * @param sourceDir        源文件夹路径
     * @param zipFile          目标ZIP文件路径
     * @param compressionLevel 压缩级别（0-9），0 表示不压缩，9 表示最佳压缩
     * @throws IOException 如果发生I/O异常
     */
    public static void zipDirectory(Path sourceDir, Path zipFile, int compressionLevel) throws IOException {
        // 获取文件夹的名称，用于在ZIP文件中保留该文件夹
        String folderName = sourceDir.getFileName().toString();
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // 设置压缩级别
            zos.setLevel(compressionLevel);
            Files.walk(sourceDir)
                    .filter(path -> !Files.isDirectory(path)) // 只处理文件，排除文件夹
                    .forEach(path -> {
                        try {
                            // 获取相对路径，并将父文件夹名称添加到路径前
                            Path relativePath = sourceDir.relativize(path);
                            Path zipEntryPath = Paths.get(folderName).resolve(relativePath);

                            // 创建并添加ZIP条目，保存带有文件夹名称的相对路径
                            ZipEntry entry = new ZipEntry(zipEntryPath.toString());
                            zos.putNextEntry(entry);  // 添加条目到压缩包

                            // 将文件内容写入ZIP
                            Files.copy(path, zos);

                            zos.closeEntry(); // 关闭当前条目
                        } catch (IOException e) {
                            logger.error("压缩[{}]失败", path.getFileName(), e);  // 打印异常，但不阻止其他文件的压缩
                        }
                    });
        }
    }
}
