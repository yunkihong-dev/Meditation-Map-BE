package com.meditationmap.admin.presentation;

import com.meditationmap.admin.presentation.dto.StaffLoginRequest;
import com.meditationmap.identity.application.AuthApplicationService;
import com.meditationmap.identity.application.port.out.AccessTokenIssuer;
import com.meditationmap.identity.infrastructure.security.AuthCookieWriter;
import com.meditationmap.identity.presentation.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Auth", description = "스태프(ADMIN/DEV/EMPLOYEE) 로그인")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthApplicationService authApplicationService;
    private final AuthCookieWriter authCookieWriter;

    @Operation(summary = "스태프 로그인 (loginId + password, HttpOnly 쿠키 설정)")
    @PostMapping("/login")
    public TokenResponse login(
            @Valid @RequestBody StaffLoginRequest body, HttpServletResponse response) {
        AccessTokenIssuer.IssuedToken token =
                authApplicationService.staffLogin(body.loginId(), body.password());
        authCookieWriter.writeAdminAccessTokenCookie(response, token.accessToken());
        return new TokenResponse(token.accessToken(), token.tokenType(), token.role());
    }

    @Operation(summary = "현재 관리자 세션 정보")
    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal UserDetails user) {
        String role =
                user.getAuthorities().stream()
                        .findFirst()
                        .map(GrantedAuthority::getAuthority)
                        .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                        .orElse("ADMIN");
        return Map.of("username", user.getUsername(), "role", role);
    }

    @Operation(summary = "관리자 로그아웃")
    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        authCookieWriter.clearAdminAccessTokenCookie(response);
    }
}
