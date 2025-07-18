package cn.citizenwiki;

import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.paratranz.ParatranzApi;
import cn.citizenwiki.api.paratranz.ParatranzCache;
import cn.citizenwiki.api.paratranz.ParatranzJacksonTools;
import cn.citizenwiki.api.s3.S3Api;
import cn.citizenwiki.api.s3.S3Config;
import cn.citizenwiki.config.GlobalConfig;
import cn.citizenwiki.config.JGitConfig;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZFile;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import cn.citizenwiki.processor.translation.*;
import cn.citizenwiki.utils.GlobalIniUtil;
import cn.citizenwiki.utils.ParatranzFileUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
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
    private static final ParatranzApi paratranzApi = ParatranzApi.INSTANCE;
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

    public static void main(String[] args) throws Exception {
        //更新pz缓存
        List<PZFile> pzFiles = ParatranzCache.INSTANCE.restorePatatranzCache();
        try (MergeAndConvert mergeAndConvert = new MergeAndConvert()) {
            mergeAndConvert.fetchAndMergeTranslations(pzFiles);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    private static void cloneScboxLocalization() {
        logger.info("开始克隆盒子仓库，此步时间较长请耐心等待...");
        try (Git git = Git.cloneRepository()
                .setURI("https://github.com/" + GithubConfig.INSTANCE.getTargetOwner() + "/" + GithubConfig.INSTANCE.getTargetRepo())
                .setDirectory(new File(GithubConfig.ORIGIN_DIR))
                .setCloneAllBranches(true)
                .call()) {
            Collection<Ref> branches = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
            //删除无用temp分支
            deleteTempBranch(branches, git);
        } catch (GitAPIException e) {
            logger.info("克隆盒子仓库异常", e);
            throw new RuntimeException(e);
        }
        logger.info("盒子仓库已克隆");
    }

    private static void deleteTempBranch(Collection<Ref> branches, Git git) throws GitAPIException {
        List<RefSpec> refSpecs = new ArrayList<>(branches.size());
        for (Ref branch : branches) {
            String branchName = branch.getName();
            if (branchName.startsWith("refs/remotes/origin/temp")) {
                String remoteBranchName = branchName.replace("refs/remotes/origin/", "");
                logger.info("检测到临时分支[{}],将被删除", remoteBranchName);
                git.checkout().setName(branchName).call();
                git.checkout().setName("main").call();
                git.branchDelete().setBranchNames(remoteBranchName).call();
                // 推送删除远程分支
                RefSpec refSpec = new RefSpec()
                        .setSource(null)
                        .setDestination("refs/heads/" + remoteBranchName);
                refSpecs.add(refSpec);
            }
        }
        if (!refSpecs.isEmpty()) {
            git.push().setCredentialsProvider(JGitConfig.CREDENTIALS_PROVIDER)
                    .setRefSpecs(refSpecs.toArray(new RefSpec[refSpecs.size()]))
                    .call();
        }
    }

    /**
     * 合并 Paratranz 上的所有汉化文件,并调用translationProcessors进行处理
     */
    public void fetchAndMergeTranslations(List<PZFile> pzFiles) throws IOException {
        //拉取所有汉化文件元信息
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
        //转换global.ini
        LinkedHashMap<String, String> globalIniMap = GlobalIniUtil.convertIniToMap(sourcePath);
        if (globalIniMap.isEmpty()) {
            logger.error("未从global.ini解析到条目，请检查文件是否正确");
            return;
        }
        logger.info("读取到{}行数据", globalIniMap.size());
        //将原始文件上传到存储桶
        if (GlobalConfig.SW_PUBLISH) {
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
        //克隆盒子仓库
        cloneScboxLocalization();
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
    private Map<String, PZTranslation> mergeTranslateData(LinkedHashMap<String, String> globalIniMap, List<PZFile> pzFiles) throws IOException {
        logger.info("开始拉取并合并paratranz汉化文件");
        Map<String, PZTranslation> mergedTranslateMap = new TreeMap<>();
        Map<String, PZTranslation> pzMap = new HashMap<>(globalIniMap.size());
        for (PZFile pzFile : pzFiles) {
            if (pzFile.getFolder().equals("汉化规则")) {
                //跳过非汉化文件
                continue;
            }
            //读取翻译文件缓存
            Path path = Path.of(ParatranzCache.CACHE_DIR, pzFile.getName());
            List<PZTranslation> pzTranslations = ParatranzJacksonTools.om.readValue(path.toFile(), ParatranzJacksonTools.LIST_TRANSLATION);
            pzTranslations.stream()
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
                logger.debug("key:[{}]在global.ini不存在,将使用原文:{}", entry.getKey(), fakePZTranslation);
            }
        }

        logger.info("paratranz文件合并后共有[{}]行数据", mergedTranslateMap.size());
        if (globalIniMap.size() != mergedTranslateMap.size()) {
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
