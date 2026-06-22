package com.meditationmap.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/** {@code multipart/form-data} OAuth 가입 마무리 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "OAuth 회원가입(multipart)")
public class RegisterOAuthMultipartParts {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 200)
    private String password;

    @NotBlank
    private String oauthSignupToken;

    @Schema(description = "선택. 프로필 이미지 파일")
    private MultipartFile profileImage;
}
