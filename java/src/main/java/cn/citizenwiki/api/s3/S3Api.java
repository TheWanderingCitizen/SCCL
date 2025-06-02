package cn.citizenwiki.api.s3;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.net.URI;
import java.nio.file.Path;

public class S3Api {

    public static final S3Api INSTANCE = new S3Api();

    private final S3Config config;
    private final S3Client s3Client;

    private S3Api() {
        config = S3Config.INSTANCE;
        // 创建 S3 客户端并配置 R2 Endpoint
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(this.config.getEndPoint()))
                .region(Region.AWS_GLOBAL)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(this.config.getAccessKey(), this.config.getSecretKey())))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    public PutObjectResponse putObject(String r2path, Path path) {
        // 构造 PutObjectRequest
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(this.config.getBucketName())
                .key(r2path)
                .build();

        // 上传文件
        return this.s3Client.putObject(putObjectRequest, RequestBody.fromFile(path));
    }

}
