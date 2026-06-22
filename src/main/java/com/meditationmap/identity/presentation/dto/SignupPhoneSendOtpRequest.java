package com.meditationmap.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SignupPhoneSendOtpRequest(
        @NotBlank @Schema(description = "국내 휴대전화 번호",
                example = "01012345678")
                String phone) {}
