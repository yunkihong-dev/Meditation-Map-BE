package com.meditationmap.expert.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.support.ListContractKeys;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * `GET /experts` 목록 투영(`ExpertSummaryJdbcRepository`)의 <b>자바 쪽 동작</b>을 고정한다. DB 를 띄우지 않는다.
 *
 * <h2>이 테스트가 보장하는 것</h2>
 *
 * <ul>
 *   <li>목록 한 행의 키 집합이 계약 5-3절과 정확히 같다(16키). `hidden` 도 `regionId`(단수)도 없다
 *   <li>`degrees`·`certificates`·`careers`·`programs`·`reviews` 가 항상 빈 배열이다
 *   <li>`centerSummary`·`centerPlaceId` 는 빈 값이면 키 자체가 빠진다(계약 12절 8항)
 *   <li><b>숨김 필터 문장이 `regionId=all` 분기와 지역 지정 분기 <u>양쪽</u>에 붙는다</b> — 이 조립은
 *       자바 `if/else` 로직이므로 여기서 실제로 검증된다. 한 분기에만 붙이는 회귀를 잡는다
 * </ul>
 *
 * <h2>이 테스트가 보장하지 <b>않는</b> 것 — 중요</h2>
 *
 * <p>붙은 문장이 MySQL 에서 <b>무엇을 걸러 내는지</b>는 보장하지 않는다. `hidden` 키가 없는 행이
 * 목록에 남는지, 문자열 `"true"`·숫자 `1` 이 제외되는지는 DB 안에서만 결정된다. 여기서 하는 것은
 * SQL <b>문자열</b> 검사다. 실제 행 필터링의 회귀 방어는 현재 자동화돼 있지 않다(Testcontainers 필요).
 */
@ExtendWith(MockitoExtension.class)
class ExpertSummaryJdbcRepositoryProjectionTest {

    private static final String HIDDEN_FILTER =
            "AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.hidden')), 'false')"
                    + " NOT IN ('true', '1')";

    @Mock private NamedParameterJdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ExpertSummaryJdbcRepository repository() {
        return new ExpertSummaryJdbcRepository(jdbc, objectMapper);
    }

    @Test
    @DisplayName("목록 한 행의 키 집합이 계약 5-3절 16키와 정확히 일치하고 hidden·regionId 키가 없다")
    void 목록_투영은_계약_16키를_그대로_내보낸다() throws Exception {
        JsonNode row = mapOneRow(fullResultSet());

        assertThat(fieldNames(row))
                .containsExactlyInAnyOrderElementsOf(ListContractKeys.EXPERT_LIST_KEYS);
        assertThat(row.has("hidden")).isFalse();
        // place 목록과 달리 expert 목록에는 단수 regionId 가 없다. 복수 regionIds 만 있다.
        assertThat(row.has("regionId")).isFalse();
        assertThat(row.get("regionIds").isArray()).isTrue();
    }

    @Test
    @DisplayName("degrees·certificates·careers·programs·reviews 는 목록에서 항상 빈 배열이다")
    void 목록은_중첩_컬렉션을_항상_빈_배열로_내보낸다() throws Exception {
        JsonNode row = mapOneRow(fullResultSet());

        for (String field : List.of("degrees", "certificates", "careers", "programs", "reviews")) {
            assertThat(row.get(field).isArray()).as(field).isTrue();
            assertThat(row.get(field).size()).as(field).isZero();
        }
    }

    @Test
    @DisplayName("centerSummary·centerPlaceId 는 빈 값이면 키 자체가 빠진다")
    void 센터_정보가_비면_키를_생략한다() throws Exception {
        // 센터 두 컬럼만 지정하고 나머지는 기본값(null)으로 둔다. 다른 컬럼 조회까지 엄격 검사에
        // 걸리지 않도록 LENIENT 로 만든다.
        ResultSet rs = Mockito.mock(ResultSet.class, Mockito.withSettings().strictness(Strictness.LENIENT));
        given(rs.getString("center_summary")).willReturn("");
        given(rs.getString("center_place_id")).willReturn("");

        JsonNode row = mapOneRow(rs);

        assertThat(row.has("centerSummary")).isFalse();
        assertThat(row.has("centerPlaceId")).isFalse();
        assertThat(fieldNames(row))
                .containsExactlyInAnyOrderElementsOf(
                        ListContractKeys.EXPERT_LIST_KEYS_WITHOUT_CENTER);
    }

    @Test
    @DisplayName("regionId=all 분기에도 숨김 필터 문장이 붙는다 (텍스트 검사)")
    void 전체_조회_분기에_숨김_필터가_붙는다() {
        repository().listByRegionId("all");

        assertThat(normalize(capturedSql())).contains("WHERE 1=1 " + HIDDEN_FILTER);
    }

    @Test
    @DisplayName("지역 지정 분기에도 숨김 필터 문장이 붙는다 — 한쪽에만 붙이는 회귀를 잡는다 (텍스트 검사)")
    void 지역_조회_분기에도_숨김_필터가_붙는다() {
        repository().listByRegionId("KR-11");

        String sql = normalize(capturedSql());
        assertThat(sql).contains("JSON_CONTAINS(JSON_EXTRACT(`data`, '$.regionIds')");
        assertThat(sql).contains(HIDDEN_FILTER);
    }

    // --- helpers -------------------------------------------------------------------------

    private JsonNode mapOneRow(ResultSet rs) throws SQLException {
        repository().listByRegionId("all");
        return capturedRowMapper().mapRow(rs, 0);
    }

    @SuppressWarnings("unchecked")
    private RowMapper<JsonNode> capturedRowMapper() {
        ArgumentCaptor<RowMapper<JsonNode>> captor = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(anyString(), any(SqlParameterSource.class), captor.capture());
        return captor.getValue();
    }

    private String capturedSql() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(captor.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        return captor.getValue();
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** 모든 컬럼에 값이 있는 한 행. 계약 5-3절 예시(`id:10` 김명상)와 같은 형태다. */
    private static ResultSet fullResultSet() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        given(rs.getString("id")).willReturn("qa-expert-01");
        given(rs.getString("name")).willReturn("김명상");
        given(rs.getString("avatar_url")).willReturn("https://example.test/10.png");
        given(rs.getString("intro")).willReturn("MBSR 8주 과정을 운영합니다.");
        given(rs.getString("specialties_json")).willReturn("[\"MBSR\"]");
        given(rs.getString("region_ids_json")).willReturn("[\"KR-11\"]");
        given(rs.getBoolean("has_center")).willReturn(true);
        given(rs.getString("center_summary")).willReturn("서울명상원");
        given(rs.getString("center_place_id")).willReturn("qa-place-01");
        given(rs.getString("class_types_json")).willReturn("[\"MBSR\"]");
        given(rs.getString("activity_areas_json")).willReturn("[\"KR-11\"]");
        return rs;
    }
}
