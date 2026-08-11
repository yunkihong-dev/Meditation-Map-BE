package com.meditationmap.storage.infrastructure.s3;

import org.springframework.stereotype.Component;

/**
 * 업로드 객체의 브라우저 공개 URL({@code {publicBaseUrl}/{objectKey}})을 만든다.
 *
 * <p>S3 버킷은 비공개이고 공개 읽기는 CloudFront(OAC)가 담당하므로, base 는 버킷 주소가 아니라 CloudFront 의 미디어
 * 경로다. 예전 MinIO 구성처럼 URL 에 버킷 이름을 끼워 넣으면 CloudFront 가 이미 버킷을 정해 둔 상태라 객체 키가 어긋난다.
 */
@Component
public class PublicMediaUrlResolver {

    private final String base;

    public PublicMediaUrlResolver(ObjectStorageProperties properties) {
        String configured = properties.publicBaseUrl();

        // 스토리지를 켠 채로 base 가 비어 있으면 업로드는 성공하는데 반환된 URL 이 깨진다.
        // 런타임에 이상한 URL 이 나가느니 기동 시점에 실패하는 편이 낫다.
        if (properties.enabled() && (configured == null || configured.isBlank())) {
            throw new IllegalStateException(
                    "app.storage.object-storage.public-base-url (STORAGE_PUBLIC_BASE_URL) 이 필요합니다. "
                            + "예: https://app.example.com/media");
        }

        this.base = configured == null ? "" : configured.replaceAll("/+$", "");
    }

    public String urlFor(String objectKey) {
        return base + "/" + objectKey;
    }
}
