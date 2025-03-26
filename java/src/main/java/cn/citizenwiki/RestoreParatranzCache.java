package cn.citizenwiki;

import cn.citizenwiki.api.paratranz.ParatranzApi;
import cn.citizenwiki.api.paratranz.ParatranzJacksonTools;
import cn.citizenwiki.model.dto.paratranz.response.PZFile;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RestoreParatranzCache {

    private static final Logger logger = LoggerFactory.getLogger(RestoreParatranzCache.class);

    //Paratranz Apibao包装类
    private static final ParatranzApi paratranzApi = ParatranzApi.INSTANCE;

    private static final String CACHE_PATH = "cache/paratranz";
    private static final String METADATA_FILE_NAME = "paratranz_files_metadata.info";

    public static void main(String[] args) throws Exception {
        //读取缓存中的文件
        Path metadataFilePath = Path.of(CACHE_PATH, METADATA_FILE_NAME);
        Map<String, PZFile> cachePzMap = new HashMap<>();
        if (Files.exists(metadataFilePath)){
            logger.info("读取paratranz缓存中");
            List<PZFile> cachePzFiles = ParatranzJacksonTools.om.readValue(metadataFilePath.toFile(), ParatranzJacksonTools.LIST_FILE);
            for (PZFile cachePzFile : cachePzFiles) {
                cachePzMap.put(cachePzFile.getName(), cachePzFile);
            }
        }else{
            logger.info("无paratranz缓存");
        }
        List<PZFile> newPzFiles = paratranzApi.projectFiles();
        String lastMetadata = ParatranzJacksonTools.om.writeValueAsString(newPzFiles);
        logger.info("api返回文件信息：\n{}", lastMetadata);
        //将最新信息写入metadata
        Files.writeString(metadataFilePath, lastMetadata, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        for (PZFile newPzFile : newPzFiles) {
            PZFile cachePzFile = cachePzMap.get(newPzFile.getName());
            if (Objects.nonNull(cachePzFile) && cachePzFile.getHash().equals(newPzFile.getHash())){
                logger.info("【{}】命中缓存", newPzFile.getName());
            }else{
                //将新内容写入旧文件
                List<PZTranslation> pzTranslations = paratranzApi.fileTranslation(newPzFile.getId());
                Files.writeString(Path.of(CACHE_PATH, newPzFile.getName()), ParatranzJacksonTools.om.writeValueAsString(pzTranslations));
            }
        }

    }

}
