package com.meditationmap.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProfileUpdateRequest(
        @NotBlank @Size(max = 80) String displayName,
        @Size(max = 17) List<@NotBlank String> regionIds,
        @Size(max = 12) List<@NotBlank @Size(max = 40) String> interests) {}
