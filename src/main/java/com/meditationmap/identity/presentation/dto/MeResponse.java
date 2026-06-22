package com.meditationmap.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "현재 로그인 사용자")
public record MeResponse(
        @Schema(description = "이메일 또는 스태프 loginId", example = "user@example.com") String username,
        @Schema(example = "user@example.com") String email,
        @Schema(example = "MEMBER") String role,
        @Schema(description = "대표 프로필 이미지 공개 URL") String profileImageUrl,
        @Schema(description = "가입 시각") Instant createdAt,
        @Schema(description = "화면 표시 이름") String displayName,
        List<String> regionIds,
        List<String> interests,
        @Schema(description = "전문가 전환 시 전문가 프로필 ID") String expertProfileId) {}
