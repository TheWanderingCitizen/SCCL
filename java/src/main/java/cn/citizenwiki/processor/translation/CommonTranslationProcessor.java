package cn.citizenwiki.processor.translation;


import cn.citizenwiki.config.GlobalConfig;
import cn.citizenwiki.api.github.GithubApi;
import cn.citizenwiki.api.github.GithubConfig;
import cn.citizenwiki.api.github.GithubHttpException;
import cn.citizenwiki.api.s3.S3Api;
import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.github.request.MergeRequest;
import cn.citizenwiki.model.dto.github.response.GithubPulls;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;

/**
 * 汉化处理器
 * 流程为输出汉化文件到指定仓库分支，再pull request到sctoolbox localization对应分支
 * 文件最终会提pr到https://github.com/StarCitizenToolBox/LocalizationData
 */
public abstract class CommonTranslationProcessor implements TranslationProcessor {

    //github对应版本分支
    private final String BRANCH_NAME;
    //文件输出的本地目录
    private final String OUTPUT_DIR;
    //输出文件路径
    private final String OUTPUT_PATH;
    //要拉去以及提交的git仓库url（非scbox）
    private final String GIT_REMOTE;
    //输出文件的outputstream
    private BufferedWriter bw;
    //jgit,用于拉取推送代码
    private Git git;
    private final GithubApi githubApi = GithubApi.INSTANCE;
    private final S3Api s3Api = S3Api.INSTANCE;

    public CommonTranslationProcessor(String BRANCH_NAME) {
        this.BRANCH_NAME = BRANCH_NAME;
        this.OUTPUT_DIR = GlobalConfig.OUTPUT_DIR + "/" + this.BRANCH_NAME;
        this.OUTPUT_PATH = OUTPUT_DIR + "/" + GithubConfig.CN_GLOBAL_INI_PATH;
        this.GIT_REMOTE = "https://github.com/" + GithubConfig.INSTANCE.getForkOwner() + "/" + GithubConfig.INSTANCE.getForkRepo();
    }

    @Override
    public void beforeProcess(Map<String, PZTranslation> mergedTranslateMap) {
        Path filePath = Paths.get(OUTPUT_PATH);
        try {
            //先删除目录
            FileUtil.deleteDirectory(OUTPUT_DIR);
            //同步仓库最新内容
            getLogger().info("[{}]开始fork sync[{}]分支", getProcessorName(), BRANCH_NAME);
            try {
                GithubPulls pullRequest = githubApi.createPullRequest("fork sync", GithubConfig.INSTANCE.getTargetUsername(),
                        GithubConfig.INSTANCE.getForkOwner(), GithubConfig.INSTANCE.getForkRepo(),
                        BRANCH_NAME, "fork sync");
                githubApi.mergePullRequest(GithubConfig.INSTANCE.getForkOwner(), GithubConfig.INSTANCE.getForkRepo(),
                        pullRequest.getNumber(),"fork sync", "fork sync", MergeRequest.MergeMethod.rebase);
            } catch (GithubHttpException e) {
                if (Objects.nonNull(e.getGitHubErrorResponse()) && "422".equals(e.getGitHubErrorResponse().getStatus())){
                    getLogger().info("[{}]无需fork sync[{}]分支，详情：{}", getProcessorName(), BRANCH_NAME, e.getGitHubErrorResponse());
                }else{
                    getLogger().error("[{}]fork sync[{}]分支失败，详情：{}", getProcessorName(), BRANCH_NAME, e.getGitHubErrorResponse());
                }
            }
            //拉取github fork仓库的代码
            this.git = gitCloneRepo(OUTPUT_DIR, BRANCH_NAME);
            // 使用 Files.createFile 创建目标文件
            Files.deleteIfExists(filePath);
            Files.createFile(filePath);
            bw = Files.newBufferedWriter(filePath, StandardOpenOption.WRITE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void process(PZTranslation pzTranslation) {
        processBw(pzTranslation, this.bw);
    }

    @Override
    public void afterProcess(FileVersion lastFileVersion) {
        //关闭文件流
        closeBw();
        //发布
        if (GlobalConfig.SW_PUBLISH && shouldPublish(lastFileVersion)){
            publish(lastFileVersion);
        }
    }

    private void publish(FileVersion lastFileVersion) {
        //提交并推送到fork仓库
        gitCommitAndPush(lastFileVersion);
        git.close();
        //向sctoolbox提交pull request
        getLogger().info("[{}]开始提交[{}]分支pull request", getProcessorName(), BRANCH_NAME);
        try {
            githubApi.createPullRequest(lastFileVersion.getName(), GithubConfig.INSTANCE.getForkOwner(),
                    GithubConfig.INSTANCE.getTargetUsername(), GithubConfig.INSTANCE.getTargetRepo(),
                    BRANCH_NAME, lastFileVersion.getName());
        } catch (GithubHttpException e) {
            throw new RuntimeException(e);
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
     * @param lastFileVersion 最新版本号
     * @return 存储桶存储路径
     */
    protected abstract String getBucketPath(FileVersion lastFileVersion);

    /**
     * 将输入流传递给子类
     * @param pzTranslation 翻译记录
     * @param bw 文件输出流
     */
    protected abstract void processBw(PZTranslation pzTranslation, BufferedWriter bw);

    /**
     * 关闭输出流
     */
    private void closeBw() {
        if (bw != null){
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
     */
    private Git gitCloneRepo(String outputDir, String branchName) {

        getLogger().info("[{}]开始克隆fork仓库分支[{}]，此步时间较长请耐心等待...", getProcessorName(), branchName);
        try {
            //clone
            Git git = Git.cloneRepository()
                    .setURI(GIT_REMOTE)
                    .setDirectory(new File(outputDir))
                    .setBranch("refs/heads/"+branchName)
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
        }finally {
            getLogger().info("[{}]克隆fork仓库分支[{}]结束", getProcessorName(), branchName);
        }
    }

    /**
     * 提交并推送至fork仓库
     */
    private void gitCommitAndPush(FileVersion lastFileVersion) {
        getLogger().info("[{}]开始将修改推送至fork仓库[{}]分支", getProcessorName(), BRANCH_NAME);
        if (Objects.nonNull(this.git)){
            try {
                git.add().addFilepattern(GithubConfig.CN_GLOBAL_INI_PATH).call();
                //如果没有改动，则不用提交
                if (!git.status().call().hasUncommittedChanges()){
                    return;
                }
                git.commit().setMessage(lastFileVersion.getName()).call();
                git.push()
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GithubConfig.INSTANCE.getForkOwner(), GithubConfig.INSTANCE.getToken()))
                        .setRemote(GIT_REMOTE)
                        .call();
                getLogger().info("[{}]推送fork仓库[{}]分支成功", getProcessorName(), BRANCH_NAME);
            } catch (GitAPIException e) {
                getLogger().info("[{}]推送fork仓库[{}]分支异常", getProcessorName(), BRANCH_NAME, e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 子类根据版本号是否需要推送，与开关共同决定
     * @param lastFileVersion 最新版本号
     * @return
     */
    protected abstract boolean shouldPublish(FileVersion lastFileVersion);

    /**
     * 获取子类的logger以打印日志
     * @return
     */
    protected abstract Logger getLogger();
}
