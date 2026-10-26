package com.meditationmap.placeowner.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.admin.infrastructure.web.HttpTrafficRecordingFilter;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.identity.infrastructure.security.JwtAuthenticationFilter;
import com.meditationmap.placeowner.application.ClaimSubmission;
import com.meditationmap.placeowner.application.OwnerPlaceService;
import com.meditationmap.placeowner.application.PlaceClaimQueryService;
import com.meditationmap.placeowner.application.PlaceClaimReviewService;
import com.meditationmap.placeowner.application.PlaceClaimSubmitService;
import com.meditationmap.placeowner.application.PlaceClaimViews;
import com.meditationmap.placeowner.application.PlaceEvidenceService;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.support.WebSliceBootConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 소유 신청·오너 장소 컨트롤러 계약 (계약 4·5·8절, 10절 오류 코드).
 *
 * <p>보장: 응답 키 집합이 계약과 정확히 같다(누락·추가 없음, 증빙 키·URL 없음) · 도메인 예외가 계약 HTTP 상태·code 로
 * 나간다(409 3종, 403, 400 + details.fields) · 멀티파트 파트가 서비스까지 바인딩된다.
 * <p>보장하지 않음: 시큐리티 URL 규칙(필터 off), 서비스 로직(서비스 테스트 몫).
 */
@WebMvcTest(
        controllers = {MemberPlaceClaimController.class, AdminPlaceClaimController.class, OwnerPlaceController.class},
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {HttpTrafficRecordingFilter.class, JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebSliceBootConfiguration.class)
@WithMockUser(username = "m1@example.com", roles = "MEMBER")
class PlaceClaimControllerContractTest {

    private static final String MEMBER = "11111111-1111-1111-1111-111111111111";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private MemberDirectory memberDirectory;
    @MockitoBean private PlaceClaimSubmitService submitService;
    @MockitoBean private PlaceClaimQueryService queryService;
    @MockitoBean private PlaceClaimReviewService reviewService;
    @MockitoBean private PlaceEvidenceService evidenceService;
    @MockitoBean private OwnerPlaceService ownerPlaceService;
    @MockitoBean private com.meditationmap.placestats.application.PlaceStatsQueryService statsQueryService;

    @Test
    @DisplayName("GET /me/places/{id}/stats: 오너가 아니면 403 PLACE_NOT_OWNER 이고 통계를 읽지 않는다")
    void 통계_오너_아님_403() throws Exception {
        org.mockito.BDDMockito.willThrow(new DomainArgumentException(ErrorCode.PLACE_NOT_OWNER))
                .given(ownerPlaceService)
                .requireOwnedPlace(MEMBER, "12");
        mvc.perform(get("/me/places/12/stats").param("range", "1W"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_OWNER"));
        org.mockito.Mockito.verifyNoInteractions(statsQueryService);
    }

    @BeforeEach
    void member() {
        given(memberDirectory.requireMemberId(anyString())).willReturn(MEMBER);
    }

    private static PlaceClaimViews.MyPlaceClaim mine() {
        return new PlaceClaimViews.MyPlaceClaim("57", "12", "고요명상센터", "PENDING", "010-1234-5678", "센터 대표입니다.",
                "123-45-67890", true, "사업자등록증.pdf", null, "2026-10-07T03:00:00.000Z", null);
    }

    @Test
    @DisplayName("POST /me/place-claims: 201 + MyPlaceClaim 12키, 멀티파트 파트가 서비스로 전달된다")
    void 신청_201() throws Exception {
        given(submitService.submit(eq(MEMBER), any())).willReturn(mine());
        MockMultipartFile ev = new MockMultipartFile("evidence", "사업자등록증.pdf", "application/pdf", new byte[] {1, 2});

        MvcResult r =
                mvc.perform(multipart("/me/place-claims")
                                .file(ev)
                                .param("placeId", "12")
                                .param("contact", "010-1234-5678")
                                .param("memo", "센터 대표입니다.")
                                .param("businessRegistrationNumber", "123-45-67890"))
                        .andExpect(status().isCreated())
                        .andReturn();

        assertThat(keys(body(r)))
                .containsExactlyInAnyOrder("id", "placeId", "placeName", "status", "contact", "memo",
                        "businessRegistrationNumber", "hasEvidence", "evidenceFileName", "rejectReason",
                        "createdAt", "reviewedAt");
        assertThat(body(r).get("rejectReason").isNull()).isTrue();

        ArgumentCaptor<ClaimSubmission> in = ArgumentCaptor.forClass(ClaimSubmission.class);
        org.mockito.Mockito.verify(submitService).submit(eq(MEMBER), in.capture());
        assertThat(in.getValue().placeId()).isEqualTo("12");
        assertThat(in.getValue().evidence().contentType()).isEqualTo("application/pdf");
        assertThat(in.getValue().evidence().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("409 3종과 403 이 계약 code 로 나간다 — DATA_ACCESS_FAILED(500) 이 아니다")
    void 오류_매핑() throws Exception {
        given(submitService.submit(eq(MEMBER), any()))
                .willThrow(new DomainArgumentException(ErrorCode.PLACE_CLAIM_ALREADY_PENDING));
        mvc.perform(multipart("/me/place-claims").param("placeId", "12").param("contact", "x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_CLAIM_ALREADY_PENDING"));

        given(reviewService.approve(MEMBER, "57")).willThrow(new DomainArgumentException(ErrorCode.PLACE_ALREADY_OWNED));
        mvc.perform(post("/admin/place-claims/57/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_ALREADY_OWNED"));

        given(reviewService.approve(MEMBER, "58"))
                .willThrow(new DomainArgumentException(ErrorCode.PLACE_CLAIM_ALREADY_REVIEWED));
        mvc.perform(post("/admin/place-claims/58/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_CLAIM_ALREADY_REVIEWED"));

        given(ownerPlaceService.get(MEMBER, "12")).willThrow(new DomainArgumentException(ErrorCode.PLACE_NOT_OWNER));
        mvc.perform(get("/me/places/12"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_OWNER"));
    }

    @Test
    @DisplayName("PATCH 금지 키는 400 PLACE_OWNER_FIELD_NOT_ALLOWED + details.fields 배열")
    void 금지_키_응답() throws Exception {
        given(ownerPlaceService.update(eq(MEMBER), eq("12"), any(JsonNode.class)))
                .willThrow(DetailedDomainException.fields(
                        ErrorCode.PLACE_OWNER_FIELD_NOT_ALLOWED, List.of("address", "hidden")));

        mvc.perform(patch("/me/places/12").contentType(MediaType.APPLICATION_JSON).content("{\"address\":\"x\",\"hidden\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_OWNER_FIELD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.details.fields[0]").value("address"))
                .andExpect(jsonPath("$.details.fields[1]").value("hidden"));
    }

    @Test
    @DisplayName("GET /me/place-claims/{id}: 본인 것은 MyPlaceClaim 12키, 아니면 404. /status 는 이 경로에 가려지지 않는다")
    void 내_신청_단건() throws Exception {
        given(queryService.mineOne(MEMBER, "57")).willReturn(mine());
        MvcResult r = mvc.perform(get("/me/place-claims/57")).andExpect(status().isOk()).andReturn();
        assertThat(keys(body(r))).hasSize(12).contains("id", "status", "rejectReason", "reviewedAt");

        given(queryService.mineOne(MEMBER, "99")).willThrow(new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        mvc.perform(get("/me/place-claims/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        given(queryService.status(MEMBER, "12")).willReturn(new PlaceClaimViews.ClaimStatus("12", false, false, null, true));
        mvc.perform(get("/me/place-claims/status").param("placeId", "12")).andExpect(jsonPath("$.canClaim").value(true));
    }

    @Test
    @DisplayName("GET /me/place-claims/status: 5키")
    void 상태_키() throws Exception {
        given(queryService.status(MEMBER, "12"))
                .willReturn(new PlaceClaimViews.ClaimStatus("12", false, false, null, true));
        MvcResult r = mvc.perform(get("/me/place-claims/status").param("placeId", "12")).andExpect(status().isOk()).andReturn();
        assertThat(keys(body(r))).containsExactlyInAnyOrder("placeId", "hasOwner", "isMine", "myPendingClaimId", "canClaim");
        assertThat(body(r).get("isMine").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("GET /admin/place-claims/{id}: 상세 15키, evidence 는 API 상대 경로만")
    void 관리자_상세_키() throws Exception {
        given(queryService.adminDetail("57"))
                .willReturn(new PlaceClaimViews.AdminClaimDetail("57", "12", "고요명상센터",
                        new PlaceClaimViews.Applicant("3f2a", "owner@example.com"), "PENDING", "010", "memo", null,
                        new PlaceClaimViews.Evidence("a.pdf", "application/pdf", 183422L, "/admin/place-claims/57/evidence"),
                        null, null, null, null, 1, "2026-10-07T03:00:00.000Z"));
        MvcResult r = mvc.perform(get("/admin/place-claims/57")).andExpect(status().isOk()).andReturn();
        JsonNode b = body(r);
        assertThat(keys(b)).containsExactlyInAnyOrder("id", "placeId", "placeName", "applicant", "status", "contact",
                "memo", "businessRegistrationNumber", "evidence", "rejectReason", "reviewedBy", "reviewedAt",
                "placeCurrentOwner", "otherPendingCount", "createdAt");
        assertThat(keys(b.get("evidence"))).containsExactlyInAnyOrder("fileName", "contentType", "sizeBytes", "url");
        assertThat(b.get("evidence").get("url").asText()).isEqualTo("/admin/place-claims/57/evidence");
        assertThat(b.toString()).doesNotContain("private/");
    }

    private JsonNode body(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> keys(JsonNode n) {
        List<String> k = new ArrayList<>();
        n.fieldNames().forEachRemaining(k::add);
        return k;
    }
}
