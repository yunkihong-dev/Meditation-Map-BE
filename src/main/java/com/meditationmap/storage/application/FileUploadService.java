package com.meditationmap.storage.application;

import com.meditationmap.storage.application.port.out.ObjectStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.minio.enabled", havingValue = "true")
public class FileUploadService {

    private final ObjectStoragePort objectStorage;

    public UploadedFileResult upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        try {
            ObjectStoragePort.StoredObject stored =
                    objectStorage.put(
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getBytes());
            return new UploadedFileResult(
                    stored.objectKey(),
                    stored.publicUrl(),
                    file.getContentType(),
                    file.getSize());
        } catch (Exception e) {
            throw new IllegalStateException("failed to store file", e);
        }
    }

    public record UploadedFileResult(
            String objectKey, String url, String contentType, long sizeBytes) {}
}
