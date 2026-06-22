package com.meditationmap.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/** {@code multipart/form-data} 회원가입 — 필드명과 이름이 동일해야 바인딩됩니다 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "이메일 가입(multipart)")
public class RegisterMultipartParts {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 200)
    private String password;

    @NotBlank
    private String phoneVerificationToken;

    @Schema(description = "선택. 프로필 이미지 파일")
    private MultipartFile profileImage;
}
