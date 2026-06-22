package com.meditationmap.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Schema(example = "user@example.com") String email,
        @NotBlank @Size(min = 8, max = 200) @Schema(example = "password12") String password,
        @NotBlank
                @Schema(
                        description =
                                "POST /auth/phone/signup/verify-otp 로 받은 일회성 토큰. 고정 코드(예: 111111) 우회 불가.")
                String phoneVerificationToken,
        @Size(max = 512)
                @Schema(
                        description =
                                "선택. 스토리지 objectKey 또는 multipart 가입 요청의 profileImage 파일 사용.")
                String profileImageObjectKey) {}
