package com.meditationmap.storage.infrastructure.minio;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 업로드 객체의 브라우저 공개 URL({base}/{bucket}/{key})을 만든다.
 *
 * <p>base 결정 규칙:
 *
 * <ul>
 *   <li>{@code app.storage.minio.public-base-url}(=MINIO_PUBLIC_BASE_URL)이 설정돼 있으면 그 값을 사용한다.
 *       별도 미디어 도메인/CDN 을 쓸 때만 지정한다.
 *   <li>비어 있으면 현재 요청의 scheme+host 기준 {@code /image} 로 만든다. 이미지가 API 와 동일 도메인이고
 *       nginx 가 {@code /image/} 를 MinIO 로 프록시하는 기본 구성에서는 이 자동 모드면 충분하다.
 * </ul>
 */
@Component
public class PublicMediaUrlResolver {

    private final MinioStorageProperties properties;

    public PublicMediaUrlResolver(MinioStorageProperties properties) {
        this.properties = properties;
    }

    public String urlFor(String objectKey) {
        return base() + "/" + properties.bucket() + "/" + objectKey;
    }

    private String base() {
        String configured = properties.publicBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.replaceAll("/$", "");
        }
        // 동일 도메인 가정: 현재 요청의 scheme+host(프록시면 X-Forwarded-* 반영) 기준 /image
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .replacePath("/image")
                .build()
                .toUriString();
    }
}
