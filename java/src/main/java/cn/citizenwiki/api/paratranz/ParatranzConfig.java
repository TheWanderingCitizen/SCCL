package cn.citizenwiki.api.paratranz;

/**
 * Paratranz配置类
 */
public class ParatranzConfig {

    static final ParatranzConfig INSTANCE = new ParatranzConfig();
    //项目id环境变量名称
    private static final String ENV_PZ_PROJECT_ID = "PZ_PROJECT_ID";
    //token环境变量名称
    private static final String ENV_PZ_TOKEN = "PZ_TOKEN";
    private final String projectId;
    private final String token;
    //api url公共前缀
    private final String urlPrefix;
    //file api url
    private final String urlFiles;
    private Integer retryNum = 3; //请求重试次数，包含第一次
    private Integer unitTime = 5000; //重试单位时间，单位毫秒

    private ParatranzConfig() {
        this.projectId = System.getenv(ENV_PZ_PROJECT_ID);
        if (projectId == null) {
            throw new RuntimeException("未配置" + ENV_PZ_PROJECT_ID);
        }
        this.token = System.getenv(ENV_PZ_TOKEN);
        if (token == null) {
            throw new RuntimeException("未配置" + ENV_PZ_TOKEN);
        }
        //生成好所有的url
        this.urlPrefix = "https://paratranz.cn/api/projects/" + this.projectId;
        this.urlFiles = urlPrefix + "/files";
    }

    public String getProjectId() {
        return projectId;
    }

    public String getToken() {
        return token;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public String getUrlFiles() {
        return urlFiles;
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
