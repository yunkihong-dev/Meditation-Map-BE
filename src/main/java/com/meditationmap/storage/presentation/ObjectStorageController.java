package com.meditationmap.storage.presentation;

import com.meditationmap.storage.application.FileUploadService;
import com.meditationmap.storage.presentation.dto.UploadedObjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Storage")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(name = "app.storage.minio.enabled", havingValue = "true")
public class ObjectStorageController {

    private final FileUploadService fileUploadService;

    @Operation(summary = "파일 업로드 → MinIO 저장 후 공개 URL 반환 (JWT 필요)")
    @PostMapping(value = "/storage/objects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadedObjectResponse upload(@RequestParam("file") MultipartFile file) {
        var r = fileUploadService.upload(file);
        return new UploadedObjectResponse(r.objectKey(), r.url(), r.contentType(), r.sizeBytes());
    }
}
