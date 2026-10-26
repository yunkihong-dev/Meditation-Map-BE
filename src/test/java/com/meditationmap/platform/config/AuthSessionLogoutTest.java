package com.meditationmap.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meditationmap.admin.infrastructure.web.HttpTrafficRecordingFilter;
import com.meditationmap.admin.presentation.AdminAuthController;
import com.meditationmap.identity.application.AuthApplicationService;
import com.meditationmap.identity.application.PhoneSignupVerificationApplicationService;
import com.meditationmap.identity.infrastructure.security.AuthCookieWriter;
import com.meditationmap.identity.infrastructure.security.JwtService;
import com.meditationmap.identity.infrastructure.security.NaverAwareOAuth2UserService;
import com.meditationmap.identity.infrastructure.security.OAuth2LoginSuccessHandler;
import com.meditationmap.identity.presentation.AuthController;
import com.meditationmap.support.WebSliceBootConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 버그 회귀(사용자 제보 "로그아웃해도 쿠키가 안 없어진다") — <b>앱의 실제 SecurityConfig 필터 체인</b>을 태운다.
 *
 * <p>증상: JWT 쿠키로 인증된 요청이 인증 정보를 HTTP 세션(JSESSIONID)에 저장하고, 로그아웃은 토큰 쿠키만 지운다. 그래서
 * 로그아웃 뒤에도 JSESSIONID 하나로 /me 가 200 이었다.
 *
 * <p>보장: (a) JWT 쿠키 인증은 세션에 SecurityContext 를 남기지 않는다 (b) 로그아웃 뒤 예전 세션만으로는 인증되지 않는다
 * (c) 로그아웃 응답이 세션을 무효화하고 JSESSIONID 를 만료시킨다 — 회원·관리자 둘 다.
 * <p>보장하지 않음: 실제 OAuth2 공급자 왕복(로컬 curl 로 /oauth2/authorization/google 302 만 확인), 운영 CloudFront.
 */
@WebMvcTest(
        controllers = {AuthController.class, AdminAuthController.class, AuthSessionLogoutTest.Probe.class},
        excludeFilters =
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = HttpTrafficRecordingFilter.class))
@ContextConfiguration(classes = WebSliceBootConfiguration.class)
@Import({SecurityConfig.class, JwtService.class, AuthCookieWriter.class, AuthSessionLogoutTest.Probe.class})
@TestPropertySource(
        properties = {
            "app.jwt.secret=test-secret-test-secret-test-secret-test-secret-test-secret-0123456789",
            "app.jwt.expiration-ms=3600000",
            "app.cors.allowed-origins=http://localhost:3000",
            "app.oauth2.frontend-auth-entry-url=http://localhost:3000/profile",
            "app.oauth2.frontend-callback-url=http://localhost:3000/auth/callback",
            "spring.security.oauth2.client.registration.google.client-id=x",
            "spring.security.oauth2.client.registration.google.client-secret=x"
        })
class AuthSessionLogoutTest {

    private static final String MEMBER = "m1@example.com";
    private static final String STAFF = "admin";

    @RestController
    static class Probe {
        @GetMapping("/me/session-probe")
        Map<String, String> me(@AuthenticationPrincipal UserDetails user) {
            return Map.of("username", user.getUsername());
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;

    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private AuthApplicationService authApplicationService;
    @MockitoBean private PhoneSignupVerificationApplicationService phoneSignupVerificationApplicationService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private NaverAwareOAuth2UserService naverAwareOAuth2UserService;

    @BeforeEach
    void users() {
        given(userDetailsService.loadUserByUsername(anyString())).willAnswer(inv -> {
            String name = inv.getArgument(0);
            String role = STAFF.equals(name) ? "ADMIN" : "MEMBER";
            return User.withUsername(name).password("x").roles(role).build();
        });
    }

    private Cookie memberCookie() {
        return new Cookie(AuthCookieWriter.ACCESS_TOKEN_COOKIE, jwtService.createAccessToken(MEMBER, Map.of()));
    }

    private Cookie adminCookie() {
        return new Cookie(AuthCookieWriter.ADMIN_ACCESS_TOKEN_COOKIE, jwtService.createAccessToken(STAFF, Map.of()));
    }

    @Test
    @DisplayName("(a) JWT 쿠키로 인증된 요청은 세션에 SecurityContext 를 저장하지 않는다")
    void JWT_인증은_세션을_만들지_않는다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/me/session-probe").cookie(memberCookie()).session(session)).andExpect(status().isOk());

        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT"))
                .as("JWT 인증이 세션에 저장되면 JSESSIONID 만으로 로그인 상태가 된다")
                .isNull();
    }

    @Test
    @DisplayName("(b) 로그아웃 뒤 예전 세션(JSESSIONID)만으로는 /me 가 401")
    void 로그아웃_뒤_세션만으로는_인증_안됨() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/me/session-probe").cookie(memberCookie()).session(session)).andExpect(status().isOk());
        mvc.perform(post("/auth/logout").cookie(memberCookie()).session(session)).andExpect(status().isOk());

        MockHttpSession reused = copyOf(session);
        mvc.perform(get("/me/session-probe").session(reused).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("(c) 회원 로그아웃 응답은 세션을 무효화하고 JSESSIONID 를 만료시킨다")
    void 회원_로그아웃은_세션과_JSESSIONID_를_지운다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult r = mvc.perform(post("/auth/logout").cookie(memberCookie()).session(session)).andReturn();

        assertThat(session.isInvalid()).as("로그아웃은 서버 세션을 무효화해야 한다").isTrue();
        assertThat(expiresCookie(r.getResponse(), "JSESSIONID")).as("JSESSIONID Max-Age=0 이 내려가야 한다").isTrue();
        assertThat(expiresCookie(r.getResponse(), AuthCookieWriter.ACCESS_TOKEN_COOKIE)).isTrue();
    }

    @Test
    @DisplayName("(c') 관리자 로그아웃도 세션을 무효화하고 JSESSIONID 를 만료시킨다, 관리자 JWT 도 세션에 남지 않는다")
    void 관리자_로그아웃() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/admin/auth/me").cookie(adminCookie()).session(session)).andExpect(status().isOk());
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();

        MvcResult r = mvc.perform(post("/admin/auth/logout").cookie(adminCookie()).session(session)).andReturn();
        assertThat(session.isInvalid()).isTrue();
        assertThat(expiresCookie(r.getResponse(), "JSESSIONID")).isTrue();
        assertThat(expiresCookie(r.getResponse(), AuthCookieWriter.ADMIN_ACCESS_TOKEN_COOKIE)).isTrue();
    }

    /** Set-Cookie 헤더(ResponseCookie 문자열) 또는 Servlet Cookie 로 이름=…; Max-Age=0 이 내려갔는지. */
    private static boolean expiresCookie(MockHttpServletResponse res, String name) {
        Cookie c = res.getCookie(name);
        if (c != null && c.getMaxAge() == 0) {
            return true;
        }
        List<String> headers = res.getHeaders("Set-Cookie");
        return headers.stream().anyMatch(h -> h.startsWith(name + "=") && h.contains("Max-Age=0"));
    }

    /** 무효화되지 않았다면(현재 버그) 같은 속성을 가진 세션을 다시 보낸다. 무효화됐다면 빈 세션 — 서버에 더는 없다. */
    private static MockHttpSession copyOf(MockHttpSession s) {
        MockHttpSession copy = new MockHttpSession();
        if (!s.isInvalid()) {
            java.util.Collections.list(s.getAttributeNames()).forEach(n -> copy.setAttribute(n, s.getAttribute(n)));
        }
        return copy;
    }
}
