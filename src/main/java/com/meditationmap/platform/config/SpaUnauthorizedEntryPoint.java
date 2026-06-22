package com.meditationmap.platform.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 브라우저 주소창 네비게이션(text/html)은 SPA 로그인·가입 화면으로 보내고,
 * API(fetch, Accept: application/json 등)는 401 JSON 을 유지합니다.
 */
public final class SpaUnauthorizedEntryPoint implements AuthenticationEntryPoint {

    private final String spaAuthEntryUrl;

    public SpaUnauthorizedEntryPoint(String spaAuthEntryUrl) {
        this.spaAuthEntryUrl = spaAuthEntryUrl;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        if (wantsJsonResponse(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
            return;
        }
        response.sendRedirect(spaAuthEntryUrl);
    }

    private static boolean wantsJsonResponse(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        String xrw = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equalsIgnoreCase(xrw);
    }
}
