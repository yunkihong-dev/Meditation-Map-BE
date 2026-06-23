package com.meditationmap.storage.infrastructure.minio;

import com.meditationmap.storage.application.port.out.ObjectStoragePort;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.minio.enabled", havingValue = "true")
public class MinioObjectStorageAdapter implements ObjectStoragePort {

    private final S3Client s3Client;
    private final MinioStorageProperties properties;
    private final PublicMediaUrlResolver urlResolver;

    @Override
    public StoredObject put(String originalFilename, String contentType, byte[] content) {
        String key = buildObjectKey(originalFilename);
        String ct =
                contentType != null && !contentType.isBlank()
                        ? contentType
                        : "application/octet-stream";
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(ct)
                        .build(),
                RequestBody.fromBytes(content));
        return new StoredObject(key, urlResolver.urlFor(key));
    }

    private String buildObjectKey(String originalFilename) {
        String name =
                originalFilename == null || originalFilename.isBlank()
                        ? "file"
                        : originalFilename;
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.length() > 180) {
            safe = safe.substring(safe.length() - 180);
        }
        String day = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        return "uploads/" + day + "/" + UUID.randomUUID() + "-" + safe;
    }
}
