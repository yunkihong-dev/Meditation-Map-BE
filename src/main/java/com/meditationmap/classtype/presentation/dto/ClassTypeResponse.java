package com.meditationmap.classtype.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "클래스 종류")
public record ClassTypeResponse(
        @Schema(example = "3f0c1a4e-...") String id,
        @Schema(example = "마음챙김") String name,
        @Schema(description = "전문가 화면 노출 여부") boolean active) {}
