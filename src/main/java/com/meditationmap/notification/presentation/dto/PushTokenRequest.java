package com.meditationmap.notification.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PUT·DELETE /me/push-tokens 본문 (계약 2.5). platform 은 WEB 만, 생략하면 WEB. */
public record PushTokenRequest(
        @NotBlank @Size(min = 1, max = 512) String token,
        @Pattern(regexp = "WEB", message = "platform 은 WEB 만 허용합니다.") String platform) {}
