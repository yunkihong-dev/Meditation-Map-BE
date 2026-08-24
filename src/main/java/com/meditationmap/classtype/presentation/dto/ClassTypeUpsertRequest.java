package com.meditationmap.classtype.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "클래스 종류 등록·수정")
public record ClassTypeUpsertRequest(
        @NotBlank @Size(max = 60) String name, Boolean active, Integer sortOrder) {}
