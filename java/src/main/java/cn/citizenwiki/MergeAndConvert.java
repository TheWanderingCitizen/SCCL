package cn.citizenwiki;

import cn.citizenwiki.api.paratranz.ParatranzApi;
import cn.citizenwiki.api.s3.S3Api;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.config.GlobalConfig;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZFile;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import cn.citizenwiki.processor.translation.*;
import cn.citizenwiki.utils.GlobalIniUtil;
import cn.citizenwiki.utils.ParatranzFileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hello world!
 */
public class MergeAndConvert implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MergeAndConvert.class);

    //Paratranz Apibao包装类
    private final ParatranzApi paratranzApi = ParatranzApi.INSTANCE;
    //词条处理器
    private final TranslationProcessor[] translationProcessors =
            new TranslationProcessor[]{new FullTranslationProcessor(), new HalfTranslationProcessor(), new BothTranslationProcessor(), new PinYinTranslationProcessor(), new SearchableTranslationProcessor()};

    private final S3Api s3Api = S3Api.INSTANCE;

    /**
     * processor多线程执行器
     */
    private final ThreadPoolExecutor processorExecutor;

    public MergeAndConvert() {
        //初始化processor线程池
        processorExecutor = buildProcessorExecutor();
    }

    public static void main(String[] args) {
        try(MergeAndConvert mergeAndConvert = new MergeAndConvert()) {
            mergeAndConvert.fetchAndMergeTranslations();
        }catch (Exception e) {
           logger.error(e.getMessage(), e);
        }
    }

    /**
     * 合并 Paratranz 上的所有汉化文件,并调用translationProcessors进行处理
     */
    public void fetchAndMergeTranslations() throws IOException {
        logger.info("从 Paratranz 拉取数据...");
        //拉取所有汉化文件
        List<PZFile> pzFiles = paratranzApi.projectFiles();
        logger.info("拉取到[{}]个文件", pzFiles.size());
        if (pzFiles.isEmpty()) {
            return;
        }
        //获取最新版本号
        FileVersion lastFileVersion = pzFiles.stream()
                .filter(pzFile -> ParatranzFileUtil.isFormatedName(pzFile.getName()))
                .map(FileVersion::new)
                .max(Comparator.naturalOrder())
                .get();
        logger.info("最新版本号为：{}", lastFileVersion.getName());
        //从本地读取global.ini
        logger.info("正在读取global.ini数据，此数据将作为基准数据...");
        Path sourcePath = Paths.get("global.ini");
        InputStream inputStream = Files.newInputStream(sourcePath);
        //转换global.ini
        LinkedHashMap<String, String> globalIniMap = GlobalIniUtil.convertIniToMap(inputStream);
        if (globalIniMap.isEmpty()) {
            logger.error("未从global.ini解析到条目，请检查文件是否正确");
            return;
        }
        logger.info("读取到{}行数据", globalIniMap.size());
        //将原始文件上传到存储桶
        if (GlobalConfig.SW_PUBLISH){
            String bucketPath = S3Config.ORGINAL_DIR + "/global.ini";
            logger.info("正在上传global.ini至存储桶[{}]", bucketPath);
            s3Api.putObject(bucketPath, sourcePath);
            logger.info("成功上传global.ini至存储桶[{}]", bucketPath);
        }
        //合并pz上的汉化
        Map<String, PZTranslation> mergedTranslateMap = Collections.unmodifiableMap(mergeTranslateData(globalIniMap, pzFiles));
        if (mergedTranslateMap.isEmpty()) {
            return;
        }
        //检查合并后的数据中是否缺少数据
        Set<String> loseKeys = globalIniMap.keySet().stream()
                .filter(key -> !mergedTranslateMap.containsKey(key)).collect(Collectors.toSet());
        if (!loseKeys.isEmpty()) {
            for (String loseKey : loseKeys) {
                logger.error("paratranz中缺少key:[{}]", loseKey);
            }
        }
        // 遍历mergedTranslateMap，使用注册的TranslationProcessor进行处理
        CompletableFuture[] futures = new CompletableFuture[translationProcessors.length];
        for (int i = 0; i < translationProcessors.length; i++) {
            futures[i] = CompletableFuture.runAsync(
                    new ProcessorTask(translationProcessors[i], mergedTranslateMap, lastFileVersion),
                    processorExecutor);
        }
        try {
            CompletableFuture.allOf(futures).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 合并所有汉化文件,按照key的字典序排序(原来的逻辑)
     * 这里会显得用LinkedHashMap没有必要,不过以防万一用上,就用LinkedHashMap了
     */
    private Map<String, PZTranslation> mergeTranslateData(LinkedHashMap<String, String> globalIniMap, List<PZFile> pzFiles) {
        logger.info("开始拉取并合并paratranz汉化文件");
        Map<String, PZTranslation> mergedTranslateMap = new TreeMap<>();
        Map<String, PZTranslation> pzMap = new HashMap<>(globalIniMap.size());
        for (PZFile pzFile : pzFiles) {
            if (pzFile.getFolder().equals("汉化规则")) {
                //跳过非汉化文件
                continue;
            }
            logger.info("正在拉取[{}]...",pzFile.getName());
            paratranzApi.fileTranslation(pzFile.getId())
                    .stream()
                    .collect(Collectors.toMap(PZTranslation::getKey, Function.identity(),
                            //pz相同key保留id大的
                            (v1, v2) -> v1.getId() > v2.getId() ? v1 : v2, () -> pzMap));
        }
        //遍历源global.ini，使用paranz上的翻译
        for (Map.Entry<String, String> entry : globalIniMap.entrySet()) {
            //英文原文
            String enValue = entry.getValue();
            // 只处理 global.ini 中存在的 key,这样能够过滤掉已经被删除的key
            PZTranslation pzTranslation = pzMap.get(entry.getKey());
            if (Objects.nonNull(pzTranslation)) {
                //相同key保留id大的
                PZTranslation winPz = mergedTranslateMap.compute(pzTranslation.getKey(), (key, val) -> {
                    if (val == null || val.getId() < pzTranslation.getId()) {
                        return pzTranslation;
                    } else {
                        return val;
                    }
                });
                //如果翻译文本为空，填充原文
                if (winPz.getTranslation().isBlank()) {
                    winPz.setTranslation(enValue);
                }
            } else {
                //如果paratranz上不存在，则使用英文原文
                PZTranslation fakePZTranslation = new PZTranslation();
                fakePZTranslation.setKey(entry.getKey());
                fakePZTranslation.setOriginal(enValue);
                fakePZTranslation.setTranslation(enValue);
                fakePZTranslation.setId(0L);
                mergedTranslateMap.put(entry.getKey(), fakePZTranslation);
                logger.debug("key:[{}]在global.ini不存在,将使用原文:{}", entry.getKey() , fakePZTranslation);
            }
        }

        logger.info("paratranz文件合并后共有[{}]行数据", mergedTranslateMap.size());
        if (globalIniMap.size() != mergedTranslateMap.size()){
            throw new RuntimeException("合并后行数[%d]与global.ini行数[%d]不一致,请联系开发查看问题".formatted(mergedTranslateMap.size(), globalIniMap.size()));
        }
        return mergedTranslateMap;
    }

    /**
     * 构建processor用的线程池
     *
     * @return
     */
    private ThreadPoolExecutor buildProcessorExecutor() {
        return new ThreadPoolExecutor(
                translationProcessors.length,
                translationProcessors.length,
                0L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Override
    public void close() throws Exception {
        this.processorExecutor.shutdown();
    }
}
