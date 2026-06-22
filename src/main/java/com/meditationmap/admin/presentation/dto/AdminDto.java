package com.meditationmap.admin.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class AdminDto {

    private AdminDto() {}

    public record AdminPlaceCreateRequest(@NotBlank String regionId, @NotNull JsonNode data) {}

    public record AdminPlaceUpdateRequest(@NotBlank String regionId, @NotNull JsonNode data) {}

    public record AdminExpertCreateRequest(@NotNull JsonNode data) {}

    public record AdminExpertUpdateRequest(@NotNull JsonNode data) {}

    public record AdminNoticeCreateRequest(@NotNull JsonNode payload) {}

    public record AdminNoticeUpdateRequest(@NotNull JsonNode payload) {}
}
