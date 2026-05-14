package com.meditationmap.identity.presentation;

import com.meditationmap.identity.application.AuthApplicationService;
import com.meditationmap.identity.domain.Email;
import com.meditationmap.identity.presentation.dto.LoginRequest;
import com.meditationmap.identity.presentation.dto.RegisterOAuthRequest;
import com.meditationmap.identity.presentation.dto.RegisterRequest;
import com.meditationmap.identity.presentation.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authApplicationService;

    @Operation(summary = "회원가입")
    @PostMapping("/register")
    public TokenResponse register(@Valid @RequestBody RegisterRequest body) {
        var token = authApplicationService.register(Email.of(body.email()), body.password());
        return new TokenResponse(token.accessToken(), token.tokenType());
    }

    @Operation(summary = "OAuth 신규 가입 완료 (비밀번호 설정 + 계정 생성)")
    @PostMapping("/register/oauth")
    public TokenResponse registerOAuth(@Valid @RequestBody RegisterOAuthRequest body) {
        var token =
                authApplicationService.registerWithOAuth(
                        Email.of(body.email()),
                        body.password(),
                        body.oauthSignupToken());
        return new TokenResponse(token.accessToken(), token.tokenType());
    }

    @Operation(summary = "로그인 (JWT 발급)")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest body) {
        var token = authApplicationService.login(Email.of(body.email()), body.password());
        return new TokenResponse(token.accessToken(), token.tokenType());
    }
}
