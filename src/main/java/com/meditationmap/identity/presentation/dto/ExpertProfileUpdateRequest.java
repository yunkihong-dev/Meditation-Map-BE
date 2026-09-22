package com.meditationmap.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ExpertProfileUpdateRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 4000) String intro,
        @Size(max = 20) List<@NotBlank @Size(max = 120) String> degrees,
        @Size(max = 30) List<@NotBlank @Size(max = 120) String> certificates,
        @Size(max = 30) List<@NotBlank @Size(max = 200) String> careers,
        @Size(max = 20) List<@NotBlank @Size(max = 80) String> classTypes,
        @Size(max = 17) List<@NotBlank String> regionIds,
        boolean hasCenter,
        @Size(max = 120) String centerName,
        @Size(max = 300) String centerAddress,
        @Size(max = 20) String businessRegistrationNumber,
        LocalDate businessOpeningDate,
        // 전문가 본인이 고르는 공개/숨김. true 면 공개 목록·지도에서 빠진다.
        // 키를 아예 보내지 않으면 record 의 boolean 기본값인 false(= 공개)로 해석된다.
        boolean hidden) {}
