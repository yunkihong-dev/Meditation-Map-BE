package com.meditationmap.inquiry.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInquiryRequest(
        @NotBlank @Email @Schema(example = "user@example.com") String email,
        @NotBlank @Size(max = 500) String subject,
        @NotBlank @Size(max = 20000) String body) {}
