package com.meditationmap.identity.presentation;

import com.meditationmap.identity.application.AuthApplicationService;
import com.meditationmap.identity.application.PhoneSignupVerificationApplicationService;
import com.meditationmap.identity.application.port.out.AccessTokenIssuer;
import com.meditationmap.identity.domain.Email;
import com.meditationmap.identity.infrastructure.security.AuthCookieWriter;
import com.meditationmap.identity.presentation.dto.EmailAvailabilityResponse;
import com.meditationmap.identity.presentation.dto.LoginRequest;
import com.meditationmap.identity.presentation.dto.RegisterMultipartParts;
import com.meditationmap.identity.presentation.dto.RegisterOAuthMultipartParts;
import com.meditationmap.identity.presentation.dto.RegisterOAuthRequest;
import com.meditationmap.identity.presentation.dto.RegisterRequest;
import com.meditationmap.identity.presentation.dto.SignupPhoneSendOtpRequest;
import com.meditationmap.identity.presentation.dto.SignupPhoneVerifyOtpRequest;
import com.meditationmap.identity.presentation.dto.SignupPhoneVerifyOtpResponse;
import com.meditationmap.identity.presentation.dto.TokenResponse;
import com.meditationmap.shared.exception.InfrastructureException;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.storage.application.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Auth")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthApplicationService authApplicationService;
    private final PhoneSignupVerificationApplicationService phoneSignupVerificationApplicationService;
    private final ObjectProvider<FileUploadService> signupFileUploadService;
    private final AuthCookieWriter authCookieWriter;

    @Operation(summary = "이메일 가입 가능 여부", description = "true 면 해당 이메일로 신규 가입 가능")
    @GetMapping("/email/availability")
    public EmailAvailabilityResponse emailAvailability(@RequestParam("email") String email) {
        boolean ok = authApplicationService.isEmailAvailable(Email.of(email));
        return new EmailAvailabilityResponse(ok);
    }

    @Operation(summary = "회원가입용 휴대폰 인증 요청", description = "6자리 OTP — naver 이면 SENS 문자 발송, log 이면 서버 로그")
    @PostMapping("/phone/signup/send-otp")
    public void sendSignupPhoneOtp(@Valid @RequestBody SignupPhoneSendOtpRequest body) {
        phoneSignupVerificationApplicationService.requestSignupPhoneOtp(body.phone());
    }

    @Operation(summary = "회원가입용 문자 인증 코드 확인", description =
            "응답의 phoneVerificationToken 을 회원가입 요청에 포함합니다.")
    @PostMapping("/phone/signup/verify-otp")
    public SignupPhoneVerifyOtpResponse verifySignupPhoneOtp(@Valid @RequestBody SignupPhoneVerifyOtpRequest body) {
        String token =
                phoneSignupVerificationApplicationService.confirmSignupPhoneOtp(body.phone(), body.code());
        return new SignupPhoneVerifyOtpResponse(token);
    }

    @Operation(summary = "회원가입", description = "JSON 본문. 프로필은 multipart 엔드포인트를 쓰거나 profileImageObjectKey 로 전달")
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TokenResponse registerJson(
            @Valid @RequestBody RegisterRequest body, HttpServletResponse response) {
        var token =
                authApplicationService.register(
                        Email.of(body.email()),
                        body.password(),
                        body.phoneVerificationToken(),
                        body.profileImageObjectKey());
        return writeToken(response, token);
    }

    @Operation(summary = "회원가입 (프로필 이미지 동시 업로드)", description =
            "multipart: email, password, phoneVerificationToken, 선택 profileImage 파일")
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TokenResponse registerMultipart(
            @Valid @ModelAttribute RegisterMultipartParts parts, HttpServletResponse response) {
        String profileKey = resolveOptionalProfileObjectKey(parts.getProfileImage());
        var token =
                authApplicationService.register(
                        Email.of(parts.getEmail()),
                        parts.getPassword(),
                        parts.getPhoneVerificationToken(),
                        profileKey);
        return writeToken(response, token);
    }

    @Operation(summary = "OAuth 신규 가입 완료")
    @PostMapping(value = "/register/oauth", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TokenResponse registerOAuthJson(
            @Valid @RequestBody RegisterOAuthRequest body, HttpServletResponse response) {
        var token =
                authApplicationService.registerWithOAuth(
                        Email.of(body.email()),
                        body.password(),
                        body.oauthSignupToken(),
                        body.profileImageObjectKey());
        return writeToken(response, token);
    }

    @Operation(summary = "OAuth 신규 가입 완료 (프로필 이미지 동시 업로드)",
            description = "multipart: email, password, oauthSignupToken, 선택 profileImage")
    @PostMapping(value = "/register/oauth", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TokenResponse registerOAuthMultipart(
            @Valid @ModelAttribute RegisterOAuthMultipartParts parts, HttpServletResponse response) {
        String profileKey = resolveOptionalProfileObjectKey(parts.getProfileImage());
        var token =
                authApplicationService.registerWithOAuth(
                        Email.of(parts.getEmail()),
                        parts.getPassword(),
                        parts.getOauthSignupToken(),
                        profileKey);
        return writeToken(response, token);
    }

    @Operation(summary = "로그인 (JWT 발급 + HttpOnly 쿠키)")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest body, HttpServletResponse response) {
        var token = authApplicationService.login(Email.of(body.email()), body.password());
        return writeToken(response, token);
    }

    @Operation(summary = "로그아웃 (인증 쿠키 삭제)")
    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        authCookieWriter.clearAccessTokenCookie(response);
    }

    private TokenResponse writeToken(HttpServletResponse response, AccessTokenIssuer.IssuedToken token) {
        authCookieWriter.writeAccessTokenCookie(response, token.accessToken());
        return new TokenResponse(token.accessToken(), token.tokenType(), token.role());
    }

    /** 파일이 비어 있지 않으면 스토리지에 올린 뒤 objectKey 반환 */
    private String resolveOptionalProfileObjectKey(MultipartFile profileImage) {
        if (profileImage == null || profileImage.isEmpty()) {
            return null;
        }
        FileUploadService upload = signupFileUploadService.getIfAvailable();
        if (upload == null) {
            throw new InfrastructureException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        return upload.upload(profileImage).objectKey();
    }
}
