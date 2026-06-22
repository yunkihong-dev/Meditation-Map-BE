package com.meditationmap.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인/회원가입 성공 시 액세스 토큰")
public record TokenResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "MEMBER") String role) {}
