package com.meditationmap.platform.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/** Spring 기본 /login?error 로 가지 않고 SPA 로그인·가입 화면으로 넘깁니다. */
@Slf4j
public final class OAuth2LoginFailureRedirectHandler implements AuthenticationFailureHandler {

    private final String spaAuthEntryUrl;

    public OAuth2LoginFailureRedirectHandler(String spaAuthEntryUrl) {
        this.spaAuthEntryUrl = spaAuthEntryUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        // 실패를 조용히 리다이렉트만 하면 사용자에게는 "처음 화면으로 되돌아간 것"으로만 보이고
        // 서버에는 아무 흔적이 남지 않아 원인 추적이 어렵다. 한 줄이라도 남긴다.
        log.warn("소셜 로그인 실패 uri={} : {}", request.getRequestURI(), exception.getMessage());

        String base = spaAuthEntryUrl.trim();
        if (base.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String msg = exception.getMessage() != null ? exception.getMessage() : "oauth_failed";
        String sep = base.contains("?") ? "&" : "?";
        String url =
                base
                        + sep
                        + "error="
                        + URLEncoder.encode("oauth_login_failed", StandardCharsets.UTF_8)
                        + "&error_detail="
                        + URLEncoder.encode(msg, StandardCharsets.UTF_8);
        response.sendRedirect(url);
    }
}
