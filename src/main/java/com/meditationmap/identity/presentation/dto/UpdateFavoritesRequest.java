package com.meditationmap.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateFavoritesRequest(
        @NotNull @Schema(description = "전체 교체할 장소 id 목록") List<String> placeIds) {}
