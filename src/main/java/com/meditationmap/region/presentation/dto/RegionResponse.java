package com.meditationmap.region.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지역 (FE Region 타입과 동일)")
public record RegionResponse(
        @Schema(example = "KR-11") String id,
        @Schema(example = "서울") String name,
        @Schema(example = "seoul") String slug) {}
