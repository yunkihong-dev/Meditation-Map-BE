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

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public void writeAccessTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie =
                ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .path("/")
                        .maxAge(Duration.ofMillis(expirationMs))
                        .sameSite("Lax")
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
                        .sameSite("Lax")
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie =
                ResponseCookie.from(name, "")
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .path("/")
                        .maxAge(Duration.ZERO)
                        .sameSite("Lax")
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
