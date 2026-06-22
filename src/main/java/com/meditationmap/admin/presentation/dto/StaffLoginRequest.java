package com.meditationmap.admin.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record StaffLoginRequest(
        @NotBlank @Schema(example = "admin") String loginId,
        @NotBlank @Schema(example = "admin") String password) {}
