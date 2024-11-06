package cn.citizenwiki;

import cn.citizenwiki.api.github.GithubApi;
import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.paratranz.ParatranzApi;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.PZFile;
import cn.citizenwiki.model.dto.paratranz.PZTranslation;
import cn.citizenwiki.processor.translation.*;
import cn.citizenwiki.utils.GlobalIniUtil;
import cn.citizenwiki.utils.ParatranzFileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Hello world!
 */
public class MergeAndConvert {

    private static final Logger logger = LoggerFactory.getLogger(MergeAndConvert.class);

    //Paratranz Apibao包装类
    private final ParatranzApi paratranzApi = ParatranzApi.INSTANCE;
    //词条处理器
    private final TranslationProcessor[] translationProcessors =
            new TranslationProcessor[]{new FullTranslationProcessor(), new HalfTranslationProcessor(), new BothTranslationProcessor()};

    /**
     * processor多线程执行器
     */
    private final ThreadPoolExecutor processorExecutor;

    public MergeAndConvert() {
        //初始化processor线程池
        processorExecutor = buildProcessorExecutor();
    }

    public static void main(String[] args) throws IOException {
        new MergeAndConvert().fetchAndMergeTranslations();
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
//        logger.info("从fork仓库[{}]分支拉取global.ini数据，此数据将作为基准数据", GithubConfig.EN_BRANCH_NAME);
//        //从fork仓库en分支下载global.ini
//        InputStream inputStream =
//                GithubApi.INSTANCE.downloadContent(GithubConfig.INSTANCE.getForkOwner(),
//                        GithubConfig.INSTANCE.getForkRepo(), GithubConfig.EN_BRANCH_NAME, GithubConfig.EN_GLOBAL_INI_PATH);
        //从本地读取global.ini
        logger.info("正在读取global.ini数据，此数据将作为基准数据...");
        InputStream inputStream = Files.newInputStream(Paths.get("global.ini"));
        //转换global.ini
        LinkedHashMap<String, String> globalIniMap = GlobalIniUtil.convertIniToMap(inputStream);
        if (globalIniMap.isEmpty()) {
            return;
        }
        logger.info("读取到{}行数据", globalIniMap.size());
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
        processorExecutor.shutdown();
    }

    /**
     * 合并所有汉化文件,按照key的字典序排序(原来的逻辑)
     * 这里会显得用LinkedHashMap没有必要,不过以防万一用上,就用LinkedHashMap了
     */
    private Map<String, PZTranslation> mergeTranslateData(LinkedHashMap<String, String> globalIniMap, List<PZFile> pzFiles) {
        logger.info("开始拉取并合并paratranz汉化文件");
        Map<String, PZTranslation> mergedTranslateMap = new TreeMap<>();
        for (PZFile pzFile : pzFiles) {
            if (pzFile.getFolder().equals("汉化规则")) {
                //跳过非汉化文件
                continue;
            }
            logger.info("正在拉取[{}]并合并...",pzFile.getName());
            Map<String, PZTranslation> pzMap = paratranzApi.fileTranslation(pzFile.getId())
                    .stream()
                    .collect(Collectors.toMap(PZTranslation::getKey, pzTranslation -> pzTranslation));
            //遍历源global.ini，使用paranz上的翻译
            for (Map.Entry<String, String> entry : globalIniMap.entrySet()) {
                //英文原文
                String enValue = entry.getValue();
                // 只处理 global.ini 中存在的 key,这样能够过滤掉已经被删除的key
                PZTranslation pzTranslation = pzMap.get(entry.getKey());
                if (Objects.nonNull(pzTranslation)) {
                    //相同key保留id大的
                    mergedTranslateMap.compute(pzTranslation.getKey(), (key, val) -> {
                        if (val == null || val.getId() < pzTranslation.getId()) {
                            return pzTranslation;
                        } else {
                            return val;
                        }
                    });
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

}
