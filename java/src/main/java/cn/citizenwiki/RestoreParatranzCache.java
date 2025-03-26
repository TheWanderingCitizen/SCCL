package cn.citizenwiki;

import cn.citizenwiki.api.paratranz.ParatranzApi;
import cn.citizenwiki.api.paratranz.ParatranzJacksonTools;
import cn.citizenwiki.model.dto.paratranz.response.PZFile;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RestoreParatranzCache {

    private static final Logger logger = LoggerFactory.getLogger(RestoreParatranzCache.class);

    //Paratranz Apibao包装类
    private static final ParatranzApi paratranzApi = ParatranzApi.INSTANCE;

    private static final String CACHE_DIR = "cache/paratranz";
    private static final String METADATA_FILE_NAME = "paratranz_files_metadata.info";

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of(CACHE_DIR));
        //读取缓存中的文件
        Path metadataFilePath = Path.of(CACHE_DIR, METADATA_FILE_NAME);
        Map<String, PZFile> cachePzMap = new HashMap<>();
        if (Files.exists(metadataFilePath)) {
            logger.info("读取paratranz缓存中");
            List<PZFile> cachePzFiles = ParatranzJacksonTools.om.readValue(metadataFilePath.toFile(), ParatranzJacksonTools.LIST_FILE);
            for (PZFile cachePzFile : cachePzFiles) {
                cachePzMap.put(cachePzFile.getName(), cachePzFile);
            }
        } else {
            logger.info("无paratranz缓存");
        }
        List<PZFile> newPzFiles = paratranzApi.projectFiles();
        String lastMetadata = ParatranzJacksonTools.om.writeValueAsString(newPzFiles);
        logger.info("api返回文件信息：\n{}", lastMetadata);
        //将最新信息写入metadata
        Files.writeString(metadataFilePath, lastMetadata);

        for (PZFile newPzFile : newPzFiles) {
            PZFile cachePzFile = cachePzMap.get(newPzFile.getName());
            if (isSame(cachePzFile, newPzFile)) {
                logger.info("【{}】命中缓存", newPzFile.getName());
            } else {
                //将新内容写入旧文件
                List<PZTranslation> pzTranslations = paratranzApi.fileTranslation(newPzFile.getId());
                Path newPzFilePath = Path.of(CACHE_DIR, newPzFile.getName());
                if (!Files.isDirectory(newPzFilePath.getParent())) {
                    Files.createDirectories(newPzFilePath.getParent());
                }
                Files.writeString(newPzFilePath, ParatranzJacksonTools.om.writeValueAsString(pzTranslations));
            }
        }

    }

    public static boolean isSame(PZFile p1, PZFile p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        if (p1 == p2) {
            return true;
        }
        //比较hash
        if (Objects.equals(p1.getHash(), p2.getHash())) {
            return true;
        }
        //因为pz的hash可能为null，所以这里通过update和modified比较
        if (Objects.equals(p1.getUpdatedAt(), p2.getUpdatedAt())
            && Objects.equals(p1.getModifiedAt(), p2.getModifiedAt())) {
            return true;
        }
        return false;
    }


}
