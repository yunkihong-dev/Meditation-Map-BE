package com.meditationmap.expert.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.admin.infrastructure.web.HttpTrafficRecordingFilter;
import com.meditationmap.expert.application.ExpertQueryService;
import com.meditationmap.identity.infrastructure.security.JwtAuthenticationFilter;
import com.meditationmap.support.ListContractKeys;
import com.meditationmap.support.WebSliceBootConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * `ExpertController` 의 <b>응답 계약</b>을 고정한다 — 상태 코드와 응답 shape.
 *
 * <h2>이 테스트가 보장하는 것</h2>
 *
 * <ul>
 *   <li>`GET /experts` 가 서비스가 준 배열을 가공 없이 내보낸다
 *   <li>`regionId` 기본값이 `"all"` 이고 파라미터가 그대로 전달된다
 *   <li>목록 원소의 키 집합이 계약 5-3절 16키와 같고 `hidden`·`regionId`(단수) 키가 없다
 *   <li>숨김 전문가의 상세는 200 + `hidden:true` 다(계약 6절)
 *   <li>존재하지 않는 id 는 404 다
 * </ul>
 *
 * <h2>이 테스트가 보장하지 <b>않는</b> 것 — 중요</h2>
 *
 * <p>`ExpertQueryService` 를 모킹한다. <b>숨김 전문가가 목록에서 빠지는지는 보장하지 않는다.</b>
 * SQL 의 숨김 필터를 전부 지워도 이 테스트는 그대로 통과한다. 시큐리티 필터도 꺼져 있어
 * `/experts` 가 공개 엔드포인트인지도 검증하지 않는다.
 */
@WebMvcTest(
        controllers = ExpertController.class,
        // @WebMvcTest 슬라이스는 `Filter` 구현체를 전부 끌어온다. 이 둘은 컨트롤러 계약과 무관한데
        // 각자 DB·인증 빈을 생성자로 요구해 컨텍스트 로딩을 깨뜨린다. addFilters=false 라 어차피
        // 체인에 끼지도 않으므로 슬라이스에서 뺀다.
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {HttpTrafficRecordingFilter.class, JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebSliceBootConfiguration.class)
class ExpertControllerContractTest {

    @Autowired private MockMvc mvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ExpertQueryService expertQueryService;

    /** 계약 5-3절 표 그대로의 목록 한 행. */
    private static final String EXPERT_LIST_ROW =
            """
            {
              "id": "qa-expert-01",
              "name": "김명상",
              "avatarUrl": "https://example.test/10.png",
              "intro": "MBSR 8주 과정을 운영합니다.",
              "specialties": ["MBSR"],
              "regionIds": ["KR-11"],
              "hasCenter": true,
              "centerSummary": "서울명상원",
              "centerPlaceId": "qa-place-01",
              "classTypes": ["MBSR"],
              "activityAreas": ["KR-11"],
              "degrees": [],
              "certificates": [],
              "careers": [],
              "programs": [],
              "reviews": []
            }
            """;

    @Test
    @DisplayName("GET /experts 는 목록을 가공 없이 내보내고 원소 키가 계약 5-3절 16키와 일치한다")
    void 목록_응답의_키_집합이_계약과_일치한다() throws Exception {
        given(expertQueryService.listExperts("all"))
                .willReturn(List.of(objectMapper.readTree(EXPERT_LIST_ROW)));

        MvcResult result =
                mvc.perform(get("/experts"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(1))
                        .andExpect(jsonPath("$[0].id").value("qa-expert-01"))
                        .andReturn();

        JsonNode first = body(result).get(0);
        assertThat(fieldNames(first))
                .containsExactlyInAnyOrderElementsOf(ListContractKeys.EXPERT_LIST_KEYS);
        assertThat(first.has("hidden")).isFalse();
        // expert 목록에는 단수 regionId 가 없다(place 와 다르다).
        assertThat(first.has("regionId")).isFalse();
    }

    @Test
    @DisplayName("regionId 를 생략하면 서비스에 all 이 넘어간다")
    void regionId_기본값은_all_이다() throws Exception {
        given(expertQueryService.listExperts("all")).willReturn(List.of());

        mvc.perform(get("/experts")).andExpect(status().isOk());

        verify(expertQueryService).listExperts("all");
    }

    @Test
    @DisplayName("regionId 파라미터가 그대로 서비스로 전달된다")
    void regionId_파라미터가_서비스로_전달된다() throws Exception {
        given(expertQueryService.listExperts("KR-49")).willReturn(List.of());

        mvc.perform(get("/experts").param("regionId", "KR-49")).andExpect(status().isOk());

        verify(expertQueryService).listExperts("KR-49");
    }

    @Test
    @DisplayName("숨김 전문가의 상세는 200 + hidden:true 다 — 404 로 바뀌지 않았다 (계약 6절)")
    void 숨김_전문가의_상세는_여전히_200이다() throws Exception {
        JsonNode hidden =
                objectMapper.readTree(
                        "{\"id\":\"qa-expert-03\",\"name\":\"숨긴 전문가\",\"hidden\":true}");
        given(expertQueryService.findByIdOrNull("qa-expert-03")).willReturn(hidden);

        mvc.perform(get("/experts/qa-expert-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true));
    }

    @Test
    @DisplayName("존재하지 않는 전문가는 404 다")
    void 없는_전문가는_404() throws Exception {
        given(expertQueryService.findByIdOrNull("does-not-exist")).willReturn(null);

        mvc.perform(get("/experts/does-not-exist")).andExpect(status().isNotFound());
    }

    // --- helpers -------------------------------------------------------------------------

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
