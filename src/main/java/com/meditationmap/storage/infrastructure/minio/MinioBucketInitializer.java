package com.meditationmap.storage.infrastructure.minio;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;

/**
 * 버킷 생성 + 객체 공개 읽기(이미지 URL을 프론트에서 직접 로드하기 위함). 운영에서는 정책을 좁히거나 presigned URL로 전환 권장.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.minio.enabled", havingValue = "true")
public class MinioBucketInitializer implements ApplicationRunner {

    private final S3Client s3Client;
    private final MinioStorageProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        String bucket = properties.bucket();
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (AwsServiceException e) {
            String code = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "";
            if (!"BucketAlreadyExists".equals(code) && !"BucketAlreadyOwnedByYou".equals(code)) {
                throw e;
            }
        }
        String policy =
                """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """
                        .formatted(bucket)
                        .replaceAll("\\s+", " ")
                        .trim();
        s3Client.putBucketPolicy(
                PutBucketPolicyRequest.builder().bucket(bucket).policy(policy).build());
    }
}
