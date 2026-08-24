package com.meditationmap.interest.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관심사 등록·수정")
public record InterestUpsertRequest(
        @NotBlank @Size(max = 60) String name,
        @Size(max = 300) String description,
        @Size(max = 500) String imageUrl,
        Boolean active,
        Integer sortOrder) {}
