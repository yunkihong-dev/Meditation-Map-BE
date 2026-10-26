package com.meditationmap.notification.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meditationmap.admin.infrastructure.web.HttpTrafficRecordingFilter;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.identity.infrastructure.security.JwtAuthenticationFilter;
import com.meditationmap.notification.application.BroadcastService;
import com.meditationmap.notification.application.CursorPage;
import com.meditationmap.support.WebSliceBootConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 광고 발송은 ADMIN 만 (D6, 계약 3절 각주 1) — 메서드 보안({@code @PreAuthorize}) 을 켜고 확인한다.
 *
 * <p>시큐리티 필터는 끈다(기존 컨트롤러 테스트와 같은 이유 — 슬라이스는 앱 SecurityConfig 를 싣지 않는다). 그래서 "/admin/** 은
 * 스태프만" 같은 URL 규칙은 여기서 검증하지 않는다. 대신 컨트롤러에 붙은 {@code @PreAuthorize} 가 실제로 평가되는지 본다.
 */
@WebMvcTest(
        controllers = AdminNotificationController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {HttpTrafficRecordingFilter.class, JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebSliceBootConfiguration.class)
@Import(AdminNotificationControllerAuthorizationTest.MethodSecurityOn.class)
class AdminNotificationControllerAuthorizationTest {

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityOn {}

    private static final String BODY =
            "{\"requestId\":\"6f1c2b0e-3a4d-4f7e-9b1a-2c3d4e5f6a7b\",\"title\":\"가을 명상 주간\","
                    + "\"body\":\"본문\",\"linkUrl\":\"/meditation/map\",\"target\":\"ALL\"}";

    @Autowired private MockMvc mvc;

    @MockitoBean private MemberDirectory memberDirectory;
    @MockitoBean private NotificationEndpoints endpoints;
    @MockitoBean private BroadcastService broadcastService;

    @Test
    @WithMockUser(username = "dev1", roles = "DEV")
    @DisplayName("DEV 는 광고 발송 403 FORBIDDEN, 서비스는 호출되지 않는다")
    void DEV는_발송_불가() throws Exception {
        mvc.perform(post("/admin/notifications/broadcasts").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(broadcastService, never()).send(anyString(), any());
    }

    @Test
    @WithMockUser(username = "emp1", roles = "EMPLOYEE")
    @DisplayName("EMPLOYEE 도 403, 하지만 발송 이력 조회는 가능하다")
    void EMPLOYEE는_이력만() throws Exception {
        mvc.perform(post("/admin/notifications/broadcasts").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        given(broadcastService.history(any())).willReturn(new CursorPage<>(List.of(), null));
        mvc.perform(get("/admin/notifications/broadcasts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("ADMIN 은 202 + {broadcastId, recipientCount, unknownEmails}")
    void ADMIN은_발송() throws Exception {
        given(memberDirectory.requireMemberId("admin")).willReturn("admin-uuid");
        given(broadcastService.send(anyString(), any()))
                .willReturn(new BroadcastService.BroadcastResult("7", 1520, List.of()));

        mvc.perform(post("/admin/notifications/broadcasts").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.broadcastId").value("7"))
                .andExpect(jsonPath("$.recipientCount").value(1520))
                .andExpect(jsonPath("$.unknownEmails").isArray());
    }

    @Test
    @WithMockUser(username = "member", roles = "MEMBER")
    @DisplayName("비스태프는 관리자 알림 목록도 403")
    void 회원은_관리자_알림_불가() throws Exception {
        mvc.perform(get("/admin/notifications")).andExpect(status().isForbidden());
    }
}
