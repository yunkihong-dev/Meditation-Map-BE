package com.meditationmap.place.infrastructure.jdbc;

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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * `GET /places` 목록 투영(`PlaceSummaryJdbcRepository`)의 <b>자바 쪽 동작</b>을 고정한다.
 *
 * <p>DB 를 띄우지 않는다. `NamedParameterJdbcTemplate` 을 모킹해 (1) 넘어가는 SQL 문자열과
 * (2) `mapRow` 가 만들어 내는 JSON 의 키 집합을 직접 붙잡아 검사한다.
 *
 * <h2>이 테스트가 보장하는 것</h2>
 *
 * <ul>
 *   <li>목록 한 행의 키 집합이 계약 4-3절과 정확히 같다(23키). 투영에 필드를 더하거나 빼면 실패한다
 *   <li>목록에 `hidden` 키가 들어가지 않는다(계약 12절 2항)
 *   <li>`programs`·`instructors`·`detailSections` 가 항상 빈 배열이다(계약 3절)
 *   <li>좌표가 SQL NULL 이면 `lat`/`lng` 키 자체가 빠진다 — 0.0 으로 채우지 않는다(계약 12절 8항)
 *   <li>WHERE 절에 숨김 필터 <b>문자열</b>이 남아 있고, 기존 `OR` 가 괄호로 묶여 있다
 * </ul>
 *
 * <h2>이 테스트가 보장하지 <b>않는</b> 것 — 중요</h2>
 *
 * <p><b>숨김 필터가 실제로 동작하는지는 전혀 보장하지 않는다.</b> 이번 버그의 본체는
 * `JSON_EXTRACT` 가 키 없는 행에서 SQL NULL 을 돌려준다는 MySQL 의 의미론이고, 그것은 DB 안에서만
 * 평가된다. 여기서 하는 것은 "그 문장이 코드에 남아 있는가" 라는 <b>텍스트 검사</b>뿐이다. 동치로
 * 보이지만 NULL 을 다르게 접는 다른 식으로 바꿔 넣으면 이 테스트는 그대로 통과하면서 버그가 되살아난다.
 *
 * <p>행 포함/제외의 실제 회귀 방어는 현재 비어 있다. 실DB(로컬 docker-compose MySQL 8.4) 수동 판정
 * (`_workspace/03_qa_report.md`)이 유일한 근거이며, 자동화하려면 Testcontainers MySQL 이 필요하다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceSummaryJdbcRepositoryProjectionTest {

    @Mock private NamedParameterJdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PlaceSummaryJdbcRepository repository() {
        return new PlaceSummaryJdbcRepository(jdbc, objectMapper);
    }

    @Test
    @DisplayName("목록 한 행의 키 집합이 계약 4-3절 23키와 정확히 일치하고 hidden 키가 없다")
    void 목록_투영은_계약_23키를_그대로_내보낸다() throws Exception {
        JsonNode row = mapOneRow(fullResultSet());

        assertThat(fieldNames(row))
                .containsExactlyInAnyOrderElementsOf(ListContractKeys.PLACE_LIST_KEYS);
        assertThat(row.has("hidden")).isFalse();
    }

    @Test
    @DisplayName("programs·instructors·detailSections 는 목록에서 항상 빈 배열이다")
    void 목록은_중첩_컬렉션을_항상_빈_배열로_내보낸다() throws Exception {
        JsonNode row = mapOneRow(fullResultSet());

        assertThat(row.get("programs").isArray()).isTrue();
        assertThat(row.get("programs").size()).isZero();
        assertThat(row.get("instructors").isArray()).isTrue();
        assertThat(row.get("instructors").size()).isZero();
        assertThat(row.get("detailSections").isArray()).isTrue();
        assertThat(row.get("detailSections").size()).isZero();
    }

    @Test
    @DisplayName("좌표가 NULL 인 행은 lat·lng 키 자체가 빠진다 — 0.0 으로 채우지 않는다")
    void 좌표가_없으면_lat_lng_키를_생략한다() throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        // wasNull() 은 lat·lng·rating 뒤에 호출된다. 전부 NULL 로 두면 좌표는 키가 빠지고
        // rating 은 JSON null 로 남는다(키는 유지). 호출 순서에 의존하지 않도록 일괄 true 로 둔다.
        given(rs.wasNull()).willReturn(true);

        JsonNode row = mapOneRow(rs);

        assertThat(row.has("lat")).isFalse();
        assertThat(row.has("lng")).isFalse();
        assertThat(fieldNames(row))
                .containsExactlyInAnyOrderElementsOf(
                        ListContractKeys.PLACE_LIST_KEYS_WITHOUT_COORDINATES);
        // rating 은 NULL 이어도 키가 남고 JSON null 이 된다(계약 4-3: number | null).
        // 참고 — 현재 SELECT 절이 rating 을 COALESCE(..., 0) 하므로 운영에서는 이 분기에
        // 도달하지 않는다. mapRow 의 계약상 동작만 고정해 두는 것이다.
        assertThat(row.get("rating").isNull()).isTrue();
    }

    @Test
    @DisplayName("WHERE 절에 숨김 필터가 남아 있고 기존 OR 가 괄호로 묶여 있다 (텍스트 검사)")
    void WHERE_절에_숨김_필터와_괄호가_남아_있다() {
        repository().listByRegionId("all");

        String sql = capturedSql();

        // 계약 8-0 확정 판정식. 다른 동치식으로 바꾸면 여기서 잡힌다(= 바꿀 땐 계약부터 고친다).
        assertThat(normalize(sql))
                .contains(
                        "AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.hidden')), 'false')"
                                + " NOT IN ('true', '1')");
        // 괄호가 빠지면 `A OR (B AND C)` 로 읽혀 regionId=all(기본값)에서 필터가 통째로 무시된다.
        assertThat(normalize(sql)).contains("WHERE (:regionId = 'all' OR region_id = :regionId)");
    }

    // --- helpers -------------------------------------------------------------------------

    /** `jdbc.query(...)` 에 넘어간 RowMapper 를 붙잡아 ResultSet 한 행을 실제로 매핑시킨다. */
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

    /** 텍스트 비교가 들여쓰기·줄바꿈에 흔들리지 않도록 공백을 한 칸으로 접는다. */
    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** 모든 컬럼에 값이 있는 한 행. 계약 4-3절 예시(`id:1` 서울 명상센터)와 같은 형태다. */
    private static ResultSet fullResultSet() throws SQLException {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        given(rs.getString("id")).willReturn("qa-place-01");
        given(rs.getString("region_id")).willReturn("KR-11");
        given(rs.getString("name")).willReturn("서울 명상센터");
        given(rs.getString("short_description")).willReturn("도심 속 30분 명상");
        given(rs.getString("description")).willReturn("설명");
        given(rs.getString("address")).willReturn("서울특별시 종로구 1");
        given(rs.getDouble("lat")).willReturn(37.5665);
        given(rs.getDouble("lng")).willReturn(126.978);
        given(rs.wasNull()).willReturn(false);
        given(rs.getString("thumbnail_url")).willReturn("https://example.test/1.jpg");
        given(rs.getString("hashtags_json")).willReturn("[\"#도심\",\"#입문\"]");
        given(rs.getString("themes_json")).willReturn("[\"healing\"]");
        given(rs.getBoolean("has_temple_stay")).willReturn(false);
        given(rs.getString("duration")).willReturn("30분");
        given(rs.getString("admission_fee")).willReturn("무료");
        given(rs.getString("venue_kind")).willReturn("명상센터");
        given(rs.getString("org_name")).willReturn("QA명상협회");
        given(rs.getLong("view_count")).willReturn(120L);
        given(rs.getDouble("rating")).willReturn(4.5);
        given(rs.getLong("review_count")).willReturn(8L);
        given(rs.getString("external_link")).willReturn("https://example.test/");
        return rs;
    }
}
