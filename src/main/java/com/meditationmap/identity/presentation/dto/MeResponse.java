package com.meditationmap.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 로그인 사용자")
public record MeResponse(@Schema(example = "user@example.com") String email) {}
