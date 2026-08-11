package com.meditationmap.storage.infrastructure.s3;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class S3ClientConfig {

    @Bean
    @ConditionalOnProperty(name = "app.storage.object-storage.enabled", havingValue = "true")
    public S3Client s3Client(ObjectStorageProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                // 기본 자격증명 체인: EC2 인스턴스 역할 → 환경변수 → ~/.aws/credentials 순으로 찾는다.
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
