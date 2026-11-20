package org.poolc.api.project.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import javax.annotation.PostConstruct;

@Component
@ConditionalOnExpression("'${game-build.enabled:false}' == 'true' && '${game-build.s3.admin-endpoint:}' != ''")
public class GameBuildBucketInitializer {
    private final S3Client s3Client;
    private final String bucket;

    public GameBuildBucketInitializer(S3Client s3Client, @Value("${game-build.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @PostConstruct
    public void initialize() {
        waitForStorage();
        ensureBucket();
        configurePublicGameRead();
    }

    private void waitForStorage() {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                s3Client.listBuckets();
                return;
            } catch (RuntimeException error) {
                if (attempt == 29) throw error;
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("게임 파일 저장소 초기화가 중단되었습니다.", interrupted);
                }
            }
        }
    }

    private void ensureBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception missingBucket) {
            if (missingBucket.statusCode() != 404) throw missingBucket;
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    private void configurePublicGameRead() {
        String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Sid\":\"LocalGameBuildRead\",\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"s3:GetObject\",\"Resource\":\"arn:aws:s3:::"
                + bucket + "/games/*\"}]}";
        s3Client.putBucketPolicy(PutBucketPolicyRequest.builder().bucket(bucket).policy(policy).build());
    }
}
