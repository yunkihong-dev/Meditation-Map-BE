package com.meditationmap.interest.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관심사(주제)")
public record InterestResponse(
        String id,
        @Schema(example = "숲 명상") String name,
        @Schema(example = "나무 사이를 천천히 걸으며 감각을 여는 명상입니다.") String description,
        @Schema(description = "카드 배경 이미지 공개 URL") String imageUrl,
        int sortOrder,
        @Schema(description = "노출 여부") boolean active) {}
