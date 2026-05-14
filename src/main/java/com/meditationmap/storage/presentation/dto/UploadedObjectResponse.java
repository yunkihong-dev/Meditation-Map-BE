package com.meditationmap.storage.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "MinIO에 저장된 객체 메타정보")
public record UploadedObjectResponse(
        @Schema(description = "버킷 내 객체 키") String objectKey,
        @Schema(description = "브라우저에서 바로 사용 가능한 URL", example = "http://localhost:9000/meditation-media/uploads/...")
                String url,
        @Schema(description = "Content-Type") String contentType,
        @Schema(description = "바이트 크기") long sizeBytes) {}
