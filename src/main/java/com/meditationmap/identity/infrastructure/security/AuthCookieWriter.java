package com.meditationmap.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieWriter {

    public static final String ACCESS_TOKEN_COOKIE = "mm_access_token";
    public static final String ADMIN_ACCESS_TOKEN_COOKIE = "mm_admin_access_token";

    @Value("${app.jwt.cookie-secure:false}")
    private boolean cookieSecure;

    // 프론트가 API 와 다른 도메인이면(크로스사이트) None 이어야 쿠키가 저장·전송·삭제된다.
    // None 은 Secure=true 가 필수(HTTPS). 동일 도메인이면 Lax 로 둬도 된다.
    @Value("${app.jwt.cookie-same-site:Lax}")
    private String cookieSameSite;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public void writeAccessTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie =
                ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .path("/")
                        .maxAge(Duration.ofMillis(expirationMs))
                        .sameSite(cookieSameSite)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearAccessTokenCookie(HttpServletResponse response) {
        clearCookie(response, ACCESS_TOKEN_COOKIE);
    }

    public void writeAdminAccessTokenCookie(HttpServletResponse response, String token) {
        writeCookie(response, ADMIN_ACCESS_TOKEN_COOKIE, token);
    }

    public void clearAdminAccessTokenCookie(HttpServletResponse response) {
        clearCookie(response, ADMIN_ACCESS_TOKEN_COOKIE);
    }

    private void writeCookie(HttpServletResponse response, String name, String token) {
        ResponseCookie cookie =
                ResponseCookie.from(name, token)
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .path("/")
                        .maxAge(Duration.ofMillis(expirationMs))
                        .sameSite(cookieSameSite)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        // 삭제 쿠키는 set 과 동일 속성(secure/sameSite/path)이어야 브라우저가 매칭해 지운다.
        ResponseCookie cookie =
                ResponseCookie.from(name, "")
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .path("/")
                        .maxAge(Duration.ZERO)
                        .sameSite(cookieSameSite)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
