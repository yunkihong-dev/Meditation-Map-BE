package com.meditationmap.admin.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AdminDto {

    private AdminDto() {}

    public record AdminPlaceCreateRequest(@NotBlank String regionId, @NotNull JsonNode data) {}

    public record AdminPlaceUpdateRequest(@NotBlank String regionId, @NotNull JsonNode data) {}

    public record AdminExpertCreateRequest(@NotNull JsonNode data) {}

    public record AdminExpertUpdateRequest(@NotNull JsonNode data) {}

    /** 관리자가 전문가 계정을 대신 생성 (아이디·이메일·비밀번호 + 전문가 프로필 data) */
    public record AdminExpertAccountCreateRequest(
            @NotBlank
                    @Size(min = 4, max = 64)
                    @Pattern(
                            regexp = "^[A-Za-z0-9._-]+$",
                            message = "아이디는 영문·숫자와 . _ - 만 사용할 수 있습니다.")
                    @Schema(example = "expert01")
                    String loginId,
            @NotBlank @Email @Schema(example = "expert@example.com") String email,
            @NotBlank
                    @Size(min = 8, max = 72)
                    @Pattern(
                            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                            message = "비밀번호는 8자 이상이며 영문·숫자·특수문자를 모두 포함해야 합니다.")
                    @Schema(example = "Expert#2026")
                    String password,
            @NotNull JsonNode data) {}

    public record AdminNoticeCreateRequest(@NotNull JsonNode payload) {}

    public record AdminNoticeUpdateRequest(@NotNull JsonNode payload) {}
}
