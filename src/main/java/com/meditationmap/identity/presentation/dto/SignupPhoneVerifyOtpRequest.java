package com.meditationmap.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SignupPhoneVerifyOtpRequest(
        @NotBlank @Schema(example = "01012345678") String phone,
        @NotBlank @Schema(description = "SMS 6자리", example = "482391") String code) {}
