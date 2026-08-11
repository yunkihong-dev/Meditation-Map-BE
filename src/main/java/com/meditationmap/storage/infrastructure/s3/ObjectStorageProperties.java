package com.meditationmap.storage.infrastructure.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 오브젝트 스토리지(AWS S3) 설정.
 *
 * <p>자격증명은 프로퍼티로 받지 않고 AWS 기본 자격증명 체인에 맡긴다. 운영(EC2)에서는 인스턴스 역할이, 로컬에서는 환경변수나
 * {@code ~/.aws/credentials} 가 잡힌다. 덕분에 서버에도 저장소에도 장기 액세스 키가 남지 않는다.
 */
@ConfigurationProperties(prefix = "app.storage.object-storage")
public record ObjectStorageProperties(
        boolean enabled,

        /** 버킷이 위치한 리전. */
        @DefaultValue("ap-northeast-2") String region,
        String bucket,

        /**
         * 업로드 객체의 공개 URL base. 최종 URL 은 {@code {publicBaseUrl}/{objectKey}} 다.
         *
         * <p>버킷은 비공개이고 공개 읽기는 CloudFront(OAC)가 대신하므로, 여기에는 버킷 주소가 아니라 CloudFront 의 미디어 경로를
         * 넣는다. 예: {@code https://app.example.com/media}
         */
        String publicBaseUrl) {}
