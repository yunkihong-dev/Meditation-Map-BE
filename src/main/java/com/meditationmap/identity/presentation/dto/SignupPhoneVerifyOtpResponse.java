package com.meditationmap.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignupPhoneVerifyOtpResponse(
        @Schema(description = "회원가입 요청 시 phoneVerificationToken 으로 전달할 일회성 토큰", example =
                "4b5e8400-e29b-41d4-a716-446655440000")
        String phoneVerificationToken) {}
