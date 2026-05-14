package com.meditationmap.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record FavoritesResponse(@NotNull @Schema(description = "즐겨찾기 장소 id 목록") List<String> placeIds) {}
