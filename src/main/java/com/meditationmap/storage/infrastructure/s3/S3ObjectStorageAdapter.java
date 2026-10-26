package com.meditationmap.storage.infrastructure.s3;

import com.meditationmap.storage.application.port.out.ObjectStoragePort;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.object-storage.enabled", havingValue = "true")
public class S3ObjectStorageAdapter implements ObjectStoragePort {

    private final S3Client s3Client;
    private final ObjectStorageProperties properties;
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

    @Override
    public StoredObject putPrivate(
            String namespace, String originalFilename, String contentType, byte[] content) {
        String key = "private/" + namespace + "/" + datedName(originalFilename);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(normalizeContentType(contentType))
                        .build(),
                RequestBody.fromBytes(content));
        return new StoredObject(key, null);
    }

    @Override
    public ObjectContent get(String objectKey) {
        try {
            ResponseBytes<GetObjectResponse> bytes =
                    s3Client.getObjectAsBytes(
                            GetObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build());
            return new ObjectContent(bytes.asByteArray(), bytes.response().contentType());
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    private static String normalizeContentType(String contentType) {
        return contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream";
    }

    private String buildObjectKey(String originalFilename) {
        return "uploads/" + datedName(originalFilename);
    }

    private static String datedName(String originalFilename) {
        String name =
                originalFilename == null || originalFilename.isBlank()
                        ? "file"
                        : originalFilename;
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.length() > 180) {
            safe = safe.substring(safe.length() - 180);
        }
        String day = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        return day + "/" + UUID.randomUUID() + "-" + safe;
    }
}
