package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.config.GlobalConfig;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import cn.citizenwiki.utils.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.Deflater;

/**
 * 全汉化处理器
 */
public class FullTranslationProcessor extends CommonTranslationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FullTranslationProcessor.class);

    private static final Path COMPRESS_LOCALIZATION_DIR = Paths.get("data", "Localization");
    private final Path COMPRESS_FILE_PATH = Paths.get(GlobalConfig.OUTPUT_DIR, "data.zip");

    // 定义规则

    public FullTranslationProcessor() {
        super(GithubConfig.FULL_BRANCH_NAME);
    }

    /**
     * 如果是ptu版本则cdn上传到ptu目录，否则上传到full目录
     *
     * @param lastFileVersion 最新版本号
     * @return
     */
    @Override
    protected String getBucketPath(FileVersion lastFileVersion) {
        if (FileVersion.Profile.PTU.equals(GlobalConfig.SW_PROFILE)) {
            logger.info("发布通道：[{}]，全汉化版本将上传至CDN的[{}]目录", GlobalConfig.SW_PROFILE.name(), S3Config.PTU_DIR);
            return S3Config.PTU_DIR + "/global.ini";
        } else {
            return S3Config.FULL_DIR + "/global.ini";
        }
    }

    @Override
    public void processBw(PZTranslation pzTranslation, BufferedWriter bw) {
        //写入文件
        if (bw != null) {
            try {
                bw.write(pzTranslation.getKey() + "=" + pzTranslation.getTranslation());
                if (!pzTranslation.getTranslation().endsWith("\r") && !pzTranslation.getTranslation().endsWith("\n")) {
                    bw.newLine();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected boolean shouldPublish(FileVersion lastFileVersion) {
        return true;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public String getProcessorName() {
        return "全汉化处理器";
    }

    @Override
    protected void beforePublish() {
        super.beforePublish();
        try {
            // 1.将汉化文件复制到指定目录
            FileUtil.copyDirectory(Paths.get(super.OUTPUT_DIR, GithubConfig.CN_DIR), COMPRESS_LOCALIZATION_DIR.resolve(GithubConfig.CN_DIR));
            // 2.压缩指定目录
            FileUtil.zipDirectory(Paths.get("data"), COMPRESS_FILE_PATH, Deflater.BEST_COMPRESSION);
        } catch (IOException e) {
            logger.error("汉化文件压缩失败", e);
        }
    }

    @Override
    protected void publish(FileVersion lastFileVersion) {
        //走父类通用逻辑
        super.publish(lastFileVersion);
        // 推送zip压缩到存储桶
        String bucketPath = S3Config.ZIP_FILE_NAME;
        getLogger().info("开始上传压缩文件至存储桶[{}]", bucketPath);
        super.s3Api.putObject(bucketPath, Paths.get(OUTPUT_PATH));
        getLogger().info("上传压缩文件至存储桶[{}]成功", bucketPath);

    }
}
