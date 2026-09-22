package com.meditationmap.place.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.admin.infrastructure.web.HttpTrafficRecordingFilter;
import com.meditationmap.identity.infrastructure.security.JwtAuthenticationFilter;
import com.meditationmap.place.application.PlaceQueryService;
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
 * `PlaceController` 의 <b>응답 계약</b>을 고정한다 — 상태 코드와 응답 shape.
 *
 * <h2>이 테스트가 보장하는 것</h2>
 *
 * <ul>
 *   <li>`GET /places` 가 서비스가 준 배열을 <b>가공 없이</b> 그대로 내보낸다(래퍼·필드 추가 없음)
 *   <li>`regionId` 를 생략하면 서비스에 `"all"` 이 넘어간다(기본값 계약)
 *   <li>목록 원소의 키 집합이 계약 4-3절 23키와 같고 `hidden` 키가 없다
 *   <li>숨김 항목의 상세는 <b>200 + `hidden:true`</b> 이다 — 이번 변경 뒤에도 404 로 바뀌지 않는다(계약 6절)
 *   <li>존재하지 않는 id 는 404 다
 * </ul>
 *
 * <h2>이 테스트가 보장하지 <b>않는</b> 것 — 중요</h2>
 *
 * <p><b>숨김 항목이 목록에서 빠지는지는 전혀 보장하지 않는다.</b> `PlaceQueryService` 를 모킹하므로
 * 이 테스트에서 목록에 무엇이 들어오는지는 내가 준 값이 전부다. 이번 버그의 본체인 MySQL JSON 함수의
 * NULL 동작은 여기에 닿지 않는다 — 필터를 통째로 지워도 이 테스트는 통과한다.
 *
 * <p>또한 시큐리티 필터를 끄고 돈다(`addFilters = false`). 따라서 `/places` 가 실제로 공개
 * 엔드포인트인지(`SecurityConfig:96` permitAll)도 검증하지 않는다. 사유는 `_workspace/04_test_report.md`
 * 참조 — `@WebMvcTest` 슬라이스는 앱의 `SecurityConfig` 가 아니라 Boot 기본 보안을 싣기 때문에,
 * 필터를 켜면 "앱 설정과 무관한 401" 을 검증하게 된다.
 */
@WebMvcTest(
        controllers = PlaceController.class,
        // @WebMvcTest 슬라이스는 `Filter` 구현체를 전부 끌어온다. 이 둘은 컨트롤러 계약과 무관한데
        // 각자 DB·인증 빈을 생성자로 요구해 컨텍스트 로딩을 깨뜨린다. addFilters=false 라 어차피
        // 체인에 끼지도 않으므로 슬라이스에서 뺀다.
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {HttpTrafficRecordingFilter.class, JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebSliceBootConfiguration.class)
class PlaceControllerContractTest {

    @Autowired private MockMvc mvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PlaceQueryService placeQueryService;

    /** 계약 4-3절 표 그대로의 목록 한 행. 키가 기준이므로 값은 예시일 뿐이다. */
    private static final String PLACE_LIST_ROW =
            """
            {
              "id": "qa-place-01",
              "regionId": "KR-11",
              "name": "서울 명상센터",
              "shortDescription": "도심 속 30분 명상",
              "description": "설명",
              "address": "서울특별시 종로구 1",
              "lat": 37.5665,
              "lng": 126.978,
              "thumbnailUrl": "https://example.test/1.jpg",
              "hashtags": ["#도심", "#입문"],
              "themes": ["healing"],
              "hasTempleStay": false,
              "duration": "30분",
              "admissionFee": "무료",
              "venueKind": "명상센터",
              "organization": { "name": "QA명상협회" },
              "viewCount": 120,
              "rating": 4.5,
              "reviewCount": 8,
              "externalLink": "https://example.test/",
              "programs": [],
              "instructors": [],
              "detailSections": []
            }
            """;

    @Test
    @DisplayName("GET /places 는 목록을 가공 없이 내보내고 원소 키가 계약 4-3절 23키와 일치한다")
    void 목록_응답의_키_집합이_계약과_일치한다() throws Exception {
        given(placeQueryService.listPlaces("all")).willReturn(List.of(readRow()));

        MvcResult result =
                mvc.perform(get("/places"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(1))
                        .andExpect(jsonPath("$[0].id").value("qa-place-01"))
                        .andExpect(jsonPath("$[0].organization.name").value("QA명상협회"))
                        .andReturn();

        JsonNode first = body(result).get(0);
        assertThat(fieldNames(first))
                .containsExactlyInAnyOrderElementsOf(ListContractKeys.PLACE_LIST_KEYS);
        // 목록에는 hidden 키를 넣지 않는다(계약 12절 2항). 넣으면 "숨김인데 목록에 있다" 가 화면까지 간다.
        assertThat(first.has("hidden")).isFalse();
    }

    @Test
    @DisplayName("regionId 를 생략하면 서비스에 all 이 넘어간다")
    void regionId_기본값은_all_이다() throws Exception {
        given(placeQueryService.listPlaces("all")).willReturn(List.of());

        mvc.perform(get("/places")).andExpect(status().isOk());

        verify(placeQueryService).listPlaces("all");
    }

    @Test
    @DisplayName("regionId 파라미터가 그대로 서비스로 전달된다")
    void regionId_파라미터가_서비스로_전달된다() throws Exception {
        given(placeQueryService.listPlaces("KR-11")).willReturn(List.of());

        mvc.perform(get("/places").param("regionId", "KR-11")).andExpect(status().isOk());

        verify(placeQueryService).listPlaces("KR-11");
    }

    @Test
    @DisplayName("숨김 장소의 상세는 200 + hidden:true 다 — 404 로 바뀌지 않았다 (계약 6절)")
    void 숨김_장소의_상세는_여전히_200이다() throws Exception {
        JsonNode hiddenPlace =
                objectMapper.readTree("{\"id\":\"qa-place-03\",\"name\":\"숨긴 센터\",\"hidden\":true}");
        given(placeQueryService.findByIdOrNull("qa-place-03")).willReturn(hiddenPlace);

        mvc.perform(get("/places/qa-place-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true));
    }

    @Test
    @DisplayName("존재하지 않는 장소는 404 다")
    void 없는_장소는_404() throws Exception {
        given(placeQueryService.findByIdOrNull("does-not-exist")).willReturn(null);

        mvc.perform(get("/places/does-not-exist")).andExpect(status().isNotFound());
    }

    // --- helpers -------------------------------------------------------------------------

    private JsonNode readRow() throws Exception {
        return objectMapper.readTree(PLACE_LIST_ROW);
    }

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
