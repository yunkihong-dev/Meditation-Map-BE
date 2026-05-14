package com.meditationmap.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterOAuthRequest(
        @NotBlank @Email @Schema(example = "user@example.com") String email,
        @NotBlank @Size(min = 8, max = 200) @Schema(example = "password12") String password,
        @NotBlank @Schema(description = "OAuth 성공 후 발급된 oauth_signup_pending JWT") String oauthSignupToken) {}
