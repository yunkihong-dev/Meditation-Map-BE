package com.meditationmap.identity.application;

import com.meditationmap.storage.application.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * OAuth 제공자 CDN에서 프로필 이미지를 받아 업로드 스토리지에 저장합니다.
 * 신뢰할 수 없는 URL로의 SSRF 방지를 위해 호스트만 화이트리스트로 제한합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OAuthProfileImageImportApplicationService {

    private static final long MAX_BODY_BYTES = 5 * 1024 * 1024;
    static final RestClient HTTP = RestClient.create();

    private final ObjectProvider<FileUploadService> signupFileUploadService;

    /** MinIO 업로드가 비활성이거나 URL이 유효하지 않으면 null. */
    public String importFromProviderImageUrl(String profilePictureUrl) {
        FileUploadService upload = signupFileUploadService.getIfAvailable();
        if (upload == null || !StringUtils.hasText(profilePictureUrl)) {
            return null;
        }
        java.net.URI uri;
        try {
            uri = java.net.URI.create(profilePictureUrl.trim());
        } catch (Exception e) {
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return null;
        }
        String rawUi = uri.getRawUserInfo();
        if (rawUi != null && !rawUi.isBlank()) {
            return null;
        }
        String host = uri.getHost();
        if (host == null || !OAuthProfileImageHostPolicy.isAllowed(host)) {
            return null;
        }

        ResponseEntity<byte[]> response;
        try {
            response =
                    HTTP.get().uri(uri).retrieve().toEntity(byte[].class);
        } catch (Exception e) {
            log.debug("oauth profile GET failed: {}", e.toString());
            return null;
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            return null;
        }
        String contentTypeHeader = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        String contentType = parseContentType(contentTypeHeader);
        if (contentType == null || !contentType.startsWith("image/")) {
            return null;
        }

        byte[] body = response.getBody();
        if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
            return null;
        }

        String filenameHint =
                filenameHintFromUriPath(uri.getPath()).orElse(extensionFromMime(contentType));

        try {
            return upload.uploadBytes(filenameHint, contentType, body).objectKey();
        } catch (Exception e) {
            log.warn("oauth profile upload failed: {}", e.toString());
            return null;
        }
    }

    static String parseContentType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        int semi = raw.indexOf(';');
        return (semi >= 0 ? raw.substring(0, semi) : raw).trim().toLowerCase();
    }

    static java.util.Optional<String> filenameHintFromUriPath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return java.util.Optional.empty();
        }
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (!name.contains(".")) return java.util.Optional.empty();
        if (name.length() > 200) name = name.substring(name.length() - 200);
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return java.util.Optional.of(name);
    }

    static String extensionFromMime(String mime) {
        String m = mime.toLowerCase();
        if ("image/jpeg".equals(m)) return "oauth-avatar.jpg";
        if ("image/png".equals(m)) return "oauth-avatar.png";
        if ("image/webp".equals(m)) return "oauth-avatar.webp";
        if ("image/gif".equals(m)) return "oauth-avatar.gif";
        return "oauth-avatar.bin";
    }
}
