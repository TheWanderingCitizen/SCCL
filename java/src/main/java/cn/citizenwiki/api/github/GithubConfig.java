package cn.citizenwiki.api.github;

/**
 * github 推送至盒子的相关配置
 */
public class GithubConfig {


    public static final GithubConfig INSTANCE = new GithubConfig();

    //半汉化分支名
    public static final String HALF_BRANCH_NAME = "cn_e";
    //双语汉化分支名
    public static final String DUAL_BRANCH_NAME = "cn_en";
    //双语汉化分支名
    public static final String FULL_BRANCH_NAME = "main";
    //英语分支名
    public static final String EN_BRANCH_NAME = "en";
    //拼音分支名
    public static final String PINYIN_BRANCH_NAME = "cn_pinyin";

    //fork仓库的名称
    private final String forkRepo;
    //目标仓库名
    private final String targetRepo = "LocalizationData";
    //github token,用于git操作
    private final String token ; //

    public static final String BASE_API_URL = "https://api.github.com";
    //汉化文件夹名称
    public static final String CN_DIR = "chinese_(simplified)";
    public static final String CN_GLOBAL_INI_PATH = CN_DIR + "/global.ini";
    //fork仓库的用户名
    private final String forkOwner;
    //目标仓库用户名
    private final String targetOwner = "StarCitizenToolBox";

    private Integer retryNum = 3; //请求重试次数，包含第一次
    private Integer unitTime = 1000; //重试单位时间，单位毫秒


    private GithubConfig() {
        this.forkOwner = System.getenv("GITHUB_FORK_USERNAME");
        if (forkOwner == null) {
            throw new RuntimeException("未配置GITHUB_FORK_USERNAME");
        }
        this.forkRepo = System.getenv("GITHUB_FORK_REPO");
        if (forkRepo == null) {
            throw new RuntimeException("未配置GITHUB_FORK_REPO");
        }
        this.token = System.getenv("GITHUB_TOKEN");
        if (token == null) {
            throw new RuntimeException("未配置GITHUB_TOKEN");
        }
    }

    public String getForkOwner() {
        return forkOwner;
    }

    public String getForkRepo() {
        return forkRepo;
    }

    public String getTargetUsername() {
        return targetOwner;
    }

    public String getTargetRepo() {
        return targetRepo;
    }

    public String getToken() {
        return token;
    }

    public Integer getRetryNum() {
        return retryNum;
    }

    public void setRetryNum(Integer retryNum) {
        this.retryNum = retryNum;
    }

    public Integer getUnitTime() {
        return unitTime;
    }

    public void setUnitTime(Integer unitTime) {
        this.unitTime = unitTime;
    }
}
