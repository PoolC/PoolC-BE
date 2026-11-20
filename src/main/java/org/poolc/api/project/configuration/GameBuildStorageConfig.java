package org.poolc.api.project.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class GameBuildStorageConfig {
    @Bean
    public S3Presigner gameBuildS3Presigner(
            @Value("${file.s3.region:ap-northeast-2}") String region,
            @Value("${game-build.s3.presign-endpoint:}") String endpoint,
            @Value("${game-build.s3.access-key:}") String accessKey,
            @Value("${game-build.s3.secret-key:}") String secretKey) {
        S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "game-build.s3.admin-endpoint")
    public S3Client gameBuildAdminS3Client(
            @Value("${file.s3.region:ap-northeast-2}") String region,
            @Value("${game-build.s3.admin-endpoint}") String endpoint,
            @Value("${game-build.s3.access-key}") String accessKey,
            @Value("${game-build.s3.secret-key}") String secretKey) {
        return S3Client.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }
}
