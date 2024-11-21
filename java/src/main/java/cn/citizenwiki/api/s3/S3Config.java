package cn.citizenwiki.api.s3;

/**
 * github 推送至盒子的相关配置
 */
public class S3Config {


    public static final S3Config INSTANCE = new S3Config();
    public static final String BOTH_DIR = "both";
    public static final String FULL_DIR = "full";
    public static final String HALF_DIR = "half";
    public static final String PTU_DIR = "ptu";
    public static final String PINYIN_DIR = "pinyin";
    public static final String ORGINAL_DIR = "orginal";
    private final String accessKey;
    private final String secretKey;
    private final String bucketName;
    private final String endPoint;

    private S3Config() {
        this.accessKey = System.getenv("S3_ACCESS_KEY");
        if (accessKey == null) {
            throw new RuntimeException("未配置S3_ACCESS_KEY");
        }
        this.secretKey = System.getenv("S3_SECRET_KEY");
        if (secretKey == null) {
            throw new RuntimeException("未配置S3_SECRET_KEY");
        }
        this.bucketName = System.getenv("S3_BUCKET");
        if (bucketName == null) {
            throw new RuntimeException("未配置S3_BUCKET");
        }
        this.endPoint = System.getenv("S3_ENDPOINT");
        if (endPoint == null) {
            throw new RuntimeException("未配置S3_ENDPOINT");
        }
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getEndPoint() {
        return endPoint;
    }
}
