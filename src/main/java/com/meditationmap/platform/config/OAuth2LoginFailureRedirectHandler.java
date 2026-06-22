package com.meditationmap.platform.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/** Spring 기본 /login?error 로 가지 않고 SPA 로그인·가입 화면으로 넘깁니다. */
public final class OAuth2LoginFailureRedirectHandler implements AuthenticationFailureHandler {

    private final String spaAuthEntryUrl;

    public OAuth2LoginFailureRedirectHandler(String spaAuthEntryUrl) {
        this.spaAuthEntryUrl = spaAuthEntryUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
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
