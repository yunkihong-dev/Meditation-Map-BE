package com.meditationmap.centerchat.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meditationmap.admin.infrastructure.web.HttpTrafficRecordingFilter;
import com.meditationmap.centerchat.application.ChatAccess;
import com.meditationmap.centerchat.application.ChatAdminAccessLogger;
import com.meditationmap.centerchat.application.ChatMessageService;
import com.meditationmap.centerchat.application.ChatReportService;
import com.meditationmap.centerchat.application.ChatViews;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.identity.infrastructure.security.JwtAuthenticationFilter;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** DC3: 신고 열람은 ADMIN·EMPLOYEE 만, DEV 403. 신고 없는 대화는 404. */
@WebMvcTest(
        controllers = AdminCenterChatController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {HttpTrafficRecordingFilter.class, JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebSliceBootConfiguration.class)
@Import(AdminCenterChatControllerAuthorizationTest.MethodSecurityOn.class)
class AdminCenterChatControllerAuthorizationTest {

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityOn {}

    @Autowired private MockMvc mvc;
    @MockitoBean private MemberDirectory members;
    @MockitoBean private ChatReportService reports;
    @MockitoBean private ChatMessageService messages;
    @MockitoBean private ChatAccess access;
    @MockitoBean private ChatAdminAccessLogger accessLogger;

    @Test
    @WithMockUser(username = "dev1", roles = "DEV")
    @DisplayName("DEV 는 신고 목록·대화 열람 403, 서비스 호출 없음")
    void DEV_403() throws Exception {
        mvc.perform(get("/admin/center-chat-reports")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/admin/center-chats/31/messages")).andExpect(status().isForbidden());
        verifyNoInteractions(reports, messages, access);
    }

    @Test
    @WithMockUser(username = "emp1", roles = "EMPLOYEE")
    @DisplayName("EMPLOYEE 는 목록 200, 신고 없는 대화 열람 404")
    void EMPLOYEE() throws Exception {
        given(reports.list(any(), any(), any())).willReturn(new ChatViews.Page<>(List.of(), null));
        mvc.perform(get("/admin/center-chat-reports")).andExpect(status().isOk());
        given(access.loadForAdmin("31")).willThrow(new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        mvc.perform(get("/admin/center-chats/31/messages")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
