package cn.citizenwiki.processor.translation;


import cn.citizenwiki.api.github.GithubApi;
import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.github.GithubHttpException;
import cn.citizenwiki.api.s3.S3Api;
import cn.citizenwiki.config.GlobalConfig;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import cn.citizenwiki.utils.FileUtil;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/**
 * 汉化处理器
 * 流程为输出汉化文件到指定仓库分支，再pull request到sctoolbox localization对应分支
 * 文件最终会提pr到https://github.com/StarCitizenToolBox/LocalizationData
 */
public abstract class CommonTranslationProcessor implements TranslationProcessor {

    //github对应版本分支
    protected final String BRANCH_NAME;
    //文件输出的本地目录
    protected final String OUTPUT_DIR;
    //输出文件路径
    protected final String OUTPUT_PATH;
    //要拉去以及提交的git仓库url（非scbox）
    protected final String GIT_REMOTE;
    //输出文件的outputstream
    private BufferedWriter bw;
    //jgit,用于拉取推送代码
    private Git git;
    private final GithubApi githubApi = GithubApi.INSTANCE;
    protected final S3Api s3Api = S3Api.INSTANCE;
    //用于生成临时分支
    protected final ZonedDateTime startTime = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));

    private volatile String tempBranchName;

    public CommonTranslationProcessor(String branchName) {
        this.BRANCH_NAME = branchName;
        this.OUTPUT_DIR = Paths.get(GlobalConfig.OUTPUT_DIR, this.BRANCH_NAME).toString();
        this.OUTPUT_PATH = Paths.get(OUTPUT_DIR, GithubConfig.CN_GLOBAL_INI_PATH).toString();
//        this.GIT_REMOTE = "https://github.com/" + GithubConfig.INSTANCE.getForkOwner() + "/" + GithubConfig.INSTANCE.getForkRepo();
        this.GIT_REMOTE = "https://github.com/" + GithubConfig.INSTANCE.getTargetOwner() + "/" + GithubConfig.INSTANCE.getTargetRepo();
    }

    @Override
    public void beforeProcess(Map<String, PZTranslation> mergedTranslateMap, FileVersion lastFileVersion) {
        Path filePath = Paths.get(OUTPUT_PATH);
        try {
            //先删除目录
            FileUtil.deleteDirectory(OUTPUT_DIR);
            //同步仓库最新内容
            getLogger().info("[{}]开始fork sync[{}]分支", getProcessorName(), BRANCH_NAME);
//            try {
//                GithubPulls pullRequest = githubApi.createPullRequest("fork sync " + BRANCH_NAME, GithubConfig.INSTANCE.getTargetOwner(),
//                        GithubConfig.INSTANCE.getForkOwner(), GithubConfig.INSTANCE.getForkRepo(),
//                        BRANCH_NAME, "fork sync");
//                getLogger().info("fork sync pr [{}]", pullRequest.getNumber());
//                githubApi.mergePullRequest(GithubConfig.INSTANCE.getForkOwner(), GithubConfig.INSTANCE.getForkRepo(),
//                        pullRequest.getNumber(), "fork sync " + BRANCH_NAME, "fork sync", MergeRequest.MergeMethod.rebase);
//            } catch (GithubHttpException e) {
//                if (Objects.nonNull(e.getGitHubErrorResponse()) && "422".equals(e.getGitHubErrorResponse().getStatus())) {
//                    getLogger().info("[{}]无需fork sync[{}]分支，详情：{}", getProcessorName(), BRANCH_NAME, e.getGitHubErrorResponse());
//                } else {
//                    getLogger().error("[{}]fork sync[{}]分支失败，详情：{}", getProcessorName(), BRANCH_NAME, e.getGitHubErrorResponse());
//                }
//            }
            //拉取github fork仓库的代码
            this.git = gitCloneAndCheckout(OUTPUT_DIR, getTempBranchName(lastFileVersion), BRANCH_NAME);
            // 使用 Files.createFile 创建目标文件
            Files.deleteIfExists(filePath);
            Files.createFile(filePath);
            bw = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.WRITE);
            //写入bom头
            bw.write('\ufeff');
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 获取临时分支名称
     * 注意: 在处理过程中获取到的分支名应保持一致
     * @param lastFileVersion
     * @return
     */
    private String getTempBranchName(FileVersion lastFileVersion) {
        if (Objects.isNull(tempBranchName)) {
            String majorVersion = String.join(".", "" + lastFileVersion.getFirst(), "" + lastFileVersion.getMiddle(), "" + lastFileVersion.getLast());
            String yyyyMMddHHmm = startTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            tempBranchName = String.join("-", BRANCH_NAME, majorVersion, lastFileVersion.getProfile().name(), "" + lastFileVersion.getVersion(), yyyyMMddHHmm);
        }
       return tempBranchName;
    }

    @Override
    public void process(PZTranslation pzTranslation) {
        pzTranslation.setTranslation(pzTranslation.getTranslation().replace("μ", "u"));
        processBw(pzTranslation, this.bw);
    }

    @Override
    public void afterProcess(FileVersion lastFileVersion) {
        //关闭文件流
        closeBw();
        beforePublish();
        //发布
        if (GlobalConfig.SW_PUBLISH && shouldPublish(lastFileVersion)) {
            publish(lastFileVersion);
        }
    }

    protected void beforePublish() {

    }

    protected void publish(FileVersion lastFileVersion) {
        //提交并推送到fork仓库
        boolean hasChanges = gitCommitAndPush(lastFileVersion);
        git.close();
        //向sctoolbox提交pull request
        if (hasChanges) {
            getLogger().info("[{}]开始提交[{}]分支pull request", getProcessorName(), BRANCH_NAME);
            try {
                githubApi.createPullRequest(lastFileVersion.getName() + " " + BRANCH_NAME, GithubConfig.INSTANCE.getTargetOwner(),
                        getTempBranchName(lastFileVersion), GithubConfig.INSTANCE.getTargetOwner(), GithubConfig.INSTANCE.getTargetRepo(),
                        BRANCH_NAME, lastFileVersion.getName());
            } catch (GithubHttpException e) {
                throw new RuntimeException(e);
            }
        }
        getLogger().info("[{}]提交[{}]分支pull request成功", getProcessorName(), BRANCH_NAME);
        //上传至cf r2
        String bucketPath = getBucketPath(lastFileVersion);
        getLogger().info("[{}]开始上传文件至存储桶[{}]", getProcessorName(), bucketPath);
        s3Api.putObject(bucketPath, Paths.get(OUTPUT_PATH));
        getLogger().info("[{}]上传文件至存储桶[{}]成功", getProcessorName(), bucketPath);
    }

    /**
     * 获取存储桶存储路径
     *
     * @param lastFileVersion 最新版本号
     * @return 存储桶存储路径
     */
    protected abstract String getBucketPath(FileVersion lastFileVersion);

    /**
     * 将输入流传递给子类
     *
     * @param pzTranslation 翻译记录
     * @param bw            文件输出流
     */
    protected abstract void processBw(PZTranslation pzTranslation, BufferedWriter bw);

    /**
     * 关闭输出流
     */
    protected void closeBw() {
        if (bw != null) {
            try {
                bw.flush();
                bw.close();
            } catch (IOException e) {
                getLogger().warn("[{}]输出流关闭失败:", getProcessorName(), e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 拉取仓库指定分支到指定目录
     *
     * @param outputDir
     * @param branchName
     *
     * 原先的fork仓库模式先废弃，以后如果有需求再启用
     */
    @Deprecated
    private Git gitCloneRepo(String outputDir, String branchName) {

        getLogger().info("[{}]开始克隆fork仓库分支[{}]，此步时间较长请耐心等待...", getProcessorName(), branchName);
        try {
            //clone
            Git git = Git.cloneRepository()
                    .setURI(GIT_REMOTE)
                    .setDirectory(new File(outputDir))
                    .setBranch("refs/heads/" + branchName)
                    .setTransportConfigCallback(transport -> {
                        // 设置压缩
                        PackConfig packConfig = new PackConfig();
                        packConfig.setBigFileThreshold(5 * 1024 * 1024); // 设置大文件阈值为 1 MB
                        packConfig.setCompressionLevel(5); // 设置压缩级别为最高
                        transport.setPackConfig(packConfig);
                    })
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GithubConfig.INSTANCE.getForkOwner(), GithubConfig.INSTANCE.getToken()))
                    .setDepth(1)
                    .call();
            //checkout
            git.checkout()
                    .setName(branchName)
//                    .setCreateBranch(true) //当clone时指定了拉取分支时，则不用CreateBranch了
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK) // 设置跟踪远程分支
                    .setStartPoint("origin/" + branchName) // 指定远程分支
                    .call();

            return git;
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        } finally {
            getLogger().info("[{}]克隆fork仓库分支[{}]结束", getProcessorName(), branchName);
        }
    }

    /**
     * 拉取仓库指定分支到指定目录
     *
     * @param outputDir
     * @param branchName
     */
    private Git gitCloneAndCheckout(String outputDir, String tempBranchName, String branchName) {

        getLogger().info("[{}]开始克隆fork仓库分支[{}]，此步时间较长请耐心等待...", getProcessorName(), branchName);
        try {
            //clone
            Git git = Git.cloneRepository()
                    .setURI(GIT_REMOTE)
                    .setDirectory(new File(outputDir))
                    .setBranch("refs/heads/" + branchName)
                    .setTransportConfigCallback(transport -> {
                        // 设置压缩
                        PackConfig packConfig = new PackConfig();
                        packConfig.setBigFileThreshold(5 * 1024 * 1024); // 设置大文件阈值为 1 MB
                        packConfig.setCompressionLevel(5); // 设置压缩级别
                        transport.setPackConfig(packConfig);
                    })
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GithubConfig.INSTANCE.getForkOwner(), GithubConfig.INSTANCE.getToken()))
                    .setDepth(1)
                    .call();
            //checkout
            git.checkout()
                    .setName(tempBranchName)
                    .setCreateBranch(true)// 每次都是新的分支名，所以每次都新建
                    .call();
            return git;
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        } finally {
            getLogger().info("[{}]克隆fork仓库分支[{}]结束", getProcessorName(), branchName);
        }
    }

    /**
     * 提交并推送至fork仓库
     */
//    private void gitCommitAndPush(FileVersion lastFileVersion) {
//        getLogger().info("[{}]开始将修改推送至fork仓库[{}]分支", getProcessorName(), BRANCH_NAME);
//        if (Objects.nonNull(this.git)) {
//            try {
//                git.add().addFilepattern(GithubConfig.CN_GLOBAL_INI_PATH).call();
//                //如果没有改动，则不用提交
//                if (!git.status().call().hasUncommittedChanges()) {
//                    return;
//                }
//                git.commit().setMessage(lastFileVersion.getName()).call();
//                git.push()
//                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GithubConfig.INSTANCE.getForkOwner(), GithubConfig.INSTANCE.getToken()))
//                        .setRemote(GIT_REMOTE)
//                        .call();
//                getLogger().info("[{}]推送fork仓库[{}]分支成功", getProcessorName(), BRANCH_NAME);
//            } catch (GitAPIException e) {
//                getLogger().info("[{}]推送fork仓库[{}]分支异常", getProcessorName(), BRANCH_NAME, e);
//                throw new RuntimeException(e);
//            }
//        }
//    }

    /**
     * 提交并推送至盒子仓库
     */
    private boolean gitCommitAndPush(FileVersion lastFileVersion) {
        String tempBranchName = getTempBranchName(lastFileVersion);
        getLogger().info("[{}]开始提交并推送[{}]分支", getProcessorName(), tempBranchName);
        boolean hasUncommittedChanges = true;
        if (Objects.nonNull(this.git)) {
            try {
                git.add().addFilepattern(GithubConfig.CN_GLOBAL_INI_PATH).call();
                //如果没有改动，则不用提交
                if (git.status().call().hasUncommittedChanges()) {
                    git.commit().setMessage(lastFileVersion.getName()).call();
                    git.push()
                            .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GithubConfig.INSTANCE.getForkOwner(), GithubConfig.INSTANCE.getToken()))
                            .setRemote(GIT_REMOTE)
                            .call();
                    getLogger().info("[{}]推送fork仓库[{}]分支成功", getProcessorName(), BRANCH_NAME);
                }else{
                    hasUncommittedChanges = false;
                    getLogger().info("[{}]推送[{}]分支无修改，不推送", getProcessorName(), tempBranchName);
                }
            } catch (GitAPIException e) {
                getLogger().info("[{}]推送[{}]分支异常", getProcessorName(), tempBranchName, e);
                throw new RuntimeException(e);
            }
        }
        return hasUncommittedChanges;
    }

    /**
     * 子类根据版本号是否需要推送，与开关共同决定
     *
     * @param lastFileVersion 最新版本号
     * @return
     */
    protected abstract boolean shouldPublish(FileVersion lastFileVersion);

    /**
     * 获取子类的logger以打印日志
     *
     * @return
     */
    protected abstract Logger getLogger();
}
