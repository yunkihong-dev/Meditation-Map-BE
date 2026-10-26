package com.meditationmap.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/**
 * S1: 비로그인 EventSource(Accept: text/event-stream)는 302 가 아니라 401 JSON 이어야 한다(계약 2.6·12절 2항).
 * 302 로 HTML 을 주면 브라우저가 재연결을 영구히 포기한다.
 */
class SpaUnauthorizedEntryPointTest {

    private final SpaUnauthorizedEntryPoint entryPoint = new SpaUnauthorizedEntryPoint("http://localhost:3000/profile");

    @Test
    @DisplayName("Accept: text/event-stream → 401 {\"error\":\"UNAUTHORIZED\"}")
    void SSE는_401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/me/notifications/stream");
        req.addHeader("Accept", "text/event-stream");
        MockHttpServletResponse res = new MockHttpServletResponse();

        entryPoint.commence(req, res, new InsufficientAuthenticationException("x"));

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).isEqualTo("{\"error\":\"UNAUTHORIZED\"}");
        assertThat(res.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("브라우저 주소창 이동(text/html)은 기존대로 SPA 로 302")
    void HTML은_리다이렉트() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/me");
        req.addHeader("Accept", "text/html");
        MockHttpServletResponse res = new MockHttpServletResponse();

        entryPoint.commence(req, res, new InsufficientAuthenticationException("x"));

        assertThat(res.getRedirectedUrl()).isEqualTo("http://localhost:3000/profile");
    }
}
