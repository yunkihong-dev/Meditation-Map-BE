package com.meditationmap.storage.application;

import com.meditationmap.storage.application.port.out.ObjectStoragePort;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
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
            throw new DomainArgumentException(ErrorCode.FILE_REQUIRED);
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
            throw new InfrastructureException(ErrorCode.FILE_STORE_FAILED, e);
        }
    }

    public UploadedFileResult uploadBytes(String filenameHint, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new DomainArgumentException(ErrorCode.FILE_REQUIRED);
        }
        try {
            ObjectStoragePort.StoredObject stored =
                    objectStorage.put(
                            filenameHint != null ? filenameHint : "file",
                            contentType,
                            content);
            String ct =
                    contentType != null && !contentType.isBlank()
                            ? contentType
                            : "application/octet-stream";
            return new UploadedFileResult(stored.objectKey(), stored.publicUrl(), ct, content.length);
        } catch (Exception e) {
            throw new InfrastructureException(ErrorCode.FILE_STORE_FAILED, e);
        }
    }

    public record UploadedFileResult(
            String objectKey, String url, String contentType, long sizeBytes) {}
}
