package com.meditationmap.notification.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** POST /admin/notifications/broadcasts 본문 (계약 3.1). */
public record BroadcastRequest(
        @NotBlank
                @Pattern(
                        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                        message = "requestId 는 UUID 여야 합니다.")
                String requestId,
        @NotBlank @Size(min = 1, max = 100) String title,
        @NotBlank @Size(min = 1, max = 500) String body,
        String linkUrl,
        @NotNull @Pattern(regexp = "ALL|EMAILS", message = "target 은 ALL 또는 EMAILS 입니다.") String target,
        @Size(max = 200) List<@NotBlank @Email @Size(max = 255) String> emails) {}
