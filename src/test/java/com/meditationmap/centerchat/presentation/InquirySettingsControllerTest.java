package com.meditationmap.centerchat.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meditationmap.admin.infrastructure.web.HttpTrafficRecordingFilter;
import com.meditationmap.centerchat.application.ChatPlaceLookup;
import com.meditationmap.centerchat.application.InquirySettingsService;
import com.meditationmap.centerchat.infrastructure.jpa.PlaceInquirySettingJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.PlaceInquirySettingSpringDataRepository;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.identity.infrastructure.security.JwtAuthenticationFilter;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.support.WebSliceBootConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * C14 오너 문의 방식 GET/PUT + C16 공개 chat-availability 응답 shape. 실제 InquirySettingsService·검증·예외 처리기를 거치고
 * 저장소·오너 판정만 목으로 둔다.
 */
@WebMvcTest(
        controllers = {OwnerInquirySettingsController.class, PlaceChatAvailabilityController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {HttpTrafficRecordingFilter.class, JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebSliceBootConfiguration.class)
@Import({InquirySettingsService.class, InquirySettingsControllerTest.FixedClock.class})
class InquirySettingsControllerTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private MemberDirectory members;
    @MockitoBean private PlaceInquirySettingSpringDataRepository repository;
    @MockitoBean private ChatPlaceLookup places;
    @MockitoBean private PlaceOwnership ownership;

    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        PlaceJpaEntity p = new PlaceJpaEntity();
        p.setId("401");
        p.setData(om.createObjectNode().put("name", "센터"));
        given(places.find("401")).willReturn(Optional.of(p));
        given(members.requireMemberId("owner9")).willReturn("owner-id");
        given(members.requireMemberId("viewer8")).willReturn("viewer-id");
        given(ownership.isOwner("owner-id", "401")).willReturn(true);
        given(ownership.ownerOf("401")).willReturn(Optional.of("owner-id"));
        given(repository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private static PlaceInquirySettingJpaEntity row(String owner, String mode, String type, String url) {
        PlaceInquirySettingJpaEntity r = new PlaceInquirySettingJpaEntity();
        r.setPlaceId("401");
        r.setOwnerMemberId(owner);
        r.setMode(mode);
        r.setKakaoType(type);
        r.setKakaoUrl(url);
        r.setUpdatedAt(Instant.parse("2026-10-15T02:00:00Z"));
        return r;
    }

    @Test
    @WithMockUser(username = "viewer8")
    @DisplayName("오너 아님 → GET·PUT 403 PLACE_NOT_OWNER, 저장 안 함")
    void 오너_아님_403() throws Exception {
        mvc.perform(get("/me/places/401/inquiry-settings"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PLACE_NOT_OWNER"));
        mvc.perform(put("/me/places/401/inquiry-settings").contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"CHAT\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PLACE_NOT_OWNER"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @WithMockUser(username = "owner9")
    @DisplayName("없는 장소 404")
    void 없는_장소_404() throws Exception {
        given(places.find("nope")).willReturn(Optional.empty());
        mvc.perform(get("/me/places/nope/inquiry-settings"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "owner9")
    @DisplayName("설정 없으면 기본 CHAT, PUT KAKAO → 200 저장된 값, 잘못된 링크 400 details.fields.kakaoUrl")
    void 오너_조회_변경() throws Exception {
        mvc.perform(get("/me/places/401/inquiry-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value("401"))
                .andExpect(jsonPath("$.mode").value("CHAT"))
                .andExpect(jsonPath("$.kakaoType").isEmpty())
                .andExpect(jsonPath("$.kakaoUrl").isEmpty())
                .andExpect(jsonPath("$.updatedAt").isEmpty());

        mvc.perform(put("/me/places/401/inquiry-settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"KAKAO\",\"kakaoType\":\"OPEN_CHAT\",\"kakaoUrl\":\"https://open.kakao.com/o/gAbc123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("KAKAO"))
                .andExpect(jsonPath("$.kakaoType").value("OPEN_CHAT"))
                .andExpect(jsonPath("$.kakaoUrl").value("https://open.kakao.com/o/gAbc123"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-15T03:00:00.000Z"));

        mvc.perform(put("/me/places/401/inquiry-settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"KAKAO\",\"kakaoType\":\"OPEN_CHAT\",\"kakaoUrl\":\"https://open.kakao.com.evil.com/o/x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields.kakaoUrl").exists());
    }

    @Test
    @DisplayName("availability: 설정 없음 → available·CHAT, 기존 3키 유지")
    void 공개_채팅() throws Exception {
        mvc.perform(get("/places/401/chat-availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value("401"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").isEmpty())
                .andExpect(jsonPath("$.mode").value("CHAT"))
                .andExpect(jsonPath("$.kakaoType").isEmpty())
                .andExpect(jsonPath("$.kakaoUrl").isEmpty());
    }

    @Test
    @DisplayName("availability: KAKAO → available false, reason KAKAO, 링크 노출")
    void 공개_카카오() throws Exception {
        given(repository.findById("401")).willReturn(Optional.of(row("owner-id", "KAKAO", "CHANNEL", "https://pf.kakao.com/_ab/chat")));
        mvc.perform(get("/places/401/chat-availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("KAKAO"))
                .andExpect(jsonPath("$.mode").value("KAKAO"))
                .andExpect(jsonPath("$.kakaoType").value("CHANNEL"))
                .andExpect(jsonPath("$.kakaoUrl").value("https://pf.kakao.com/_ab/chat"));
    }

    @Test
    @DisplayName("availability: 숨김이면 HIDDEN 우선·링크 없음, 이전 오너가 저장한 KAKAO 는 무시(CHAT)")
    void 공개_우선순위() throws Exception {
        given(repository.findById("401")).willReturn(Optional.of(row("owner-id", "KAKAO", "CHANNEL", "https://pf.kakao.com/_ab")));
        PlaceJpaEntity hidden = new PlaceJpaEntity();
        hidden.setId("401");
        hidden.setData(om.createObjectNode().put("hidden", true));
        given(places.find("401")).willReturn(Optional.of(hidden));
        mvc.perform(get("/places/401/chat-availability"))
                .andExpect(jsonPath("$.reason").value("HIDDEN"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.kakaoUrl").isEmpty());

        PlaceJpaEntity visible = new PlaceJpaEntity();
        visible.setId("401");
        visible.setData(om.createObjectNode());
        given(places.find("401")).willReturn(Optional.of(visible));
        given(repository.findById("401")).willReturn(Optional.of(row("old-owner", "KAKAO", "CHANNEL", "https://pf.kakao.com/_ab")));
        mvc.perform(get("/places/401/chat-availability"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.mode").value("CHAT"))
                .andExpect(jsonPath("$.kakaoUrl").isEmpty());

        given(ownership.ownerOf("401")).willReturn(Optional.empty());
        mvc.perform(get("/places/401/chat-availability"))
                .andExpect(jsonPath("$.reason").value("NO_OWNER"))
                .andExpect(jsonPath("$.mode").value("CHAT"));
    }
}
