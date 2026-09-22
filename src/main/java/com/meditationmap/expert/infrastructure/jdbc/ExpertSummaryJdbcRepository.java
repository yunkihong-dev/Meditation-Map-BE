package com.meditationmap.expert.infrastructure.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 전문가 목록: programs·reviews 등 대용량 JSON은 제외하고 한 번에 조회합니다.
 */
@Repository
@RequiredArgsConstructor
public class ExpertSummaryJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<JsonNode> listByRegionId(String regionIdRaw) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where;
        if ("all".equals(regionIdRaw)) {
            where = "WHERE 1=1";
        } else {
            where =
                    "WHERE JSON_CONTAINS(JSON_EXTRACT(`data`, '$.regionIds'), JSON_QUOTE(:regionId), '$')";
            params.addValue("regionId", regionIdRaw);
        }
        /*
         * 숨김 처리된 전문가는 공개 목록에서 제외한다. 두 분기 모두에 붙어야 하므로 분기 뒤에
         * 한 번만 이어 붙인다.
         *  - hidden 키가 없는 행(운영 데이터 대다수)에서 JSON_EXTRACT 는 SQL NULL 을 돌려주므로
         *    COALESCE 로 'false' 로 접지 않으면 그 행들이 전부 사라진다.
         *  - JSON_UNQUOTE 로 문자열화해서 비교하면 boolean true·문자열 "true"·숫자 1 을 모두
         *    걸러 낸다. JSON 비교(= CAST('true' AS JSON))는 문자열/숫자 형태를 놓친다.
         * 두 분기 모두 최상위 OR 가 없어 괄호는 필요 없다.
         */
        where +=
                " AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.hidden')), 'false')"
                        + " NOT IN ('true', '1')";

        String sql =
                """
                SELECT id,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.name')), '') AS name,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.avatarUrl')), '') AS avatar_url,
                       LEFT(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.intro')), ''), 500) AS intro,
                       JSON_EXTRACT(`data`, '$.specialties') AS specialties_json,
                       JSON_EXTRACT(`data`, '$.regionIds') AS region_ids_json,
                       IF(JSON_EXTRACT(`data`, '$.hasCenter') = CAST('true' AS JSON), 1, 0) AS has_center,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.centerSummary')), '') AS center_summary,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.centerPlaceId')), '') AS center_place_id,
                       JSON_EXTRACT(`data`, '$.classTypes') AS class_types_json,
                       JSON_EXTRACT(`data`, '$.activityAreas') AS activity_areas_json
                FROM experts
                """
                        + where
                        + " ORDER BY id";

        return jdbc.query(sql, params, (rs, rowNum) -> mapRow(rs));
    }

    private JsonNode mapRow(ResultSet rs) throws SQLException {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("id", rs.getString("id"));
        n.put("name", rs.getString("name"));
        n.put("avatarUrl", rs.getString("avatar_url"));
        n.put("intro", rs.getString("intro"));
        n.set("specialties", readArray(rs.getString("specialties_json")));
        n.set("regionIds", readArray(rs.getString("region_ids_json")));
        n.put("hasCenter", rs.getBoolean("has_center"));
        String centerSummary = rs.getString("center_summary");
        if (centerSummary != null && !centerSummary.isEmpty()) {
            n.put("centerSummary", centerSummary);
        }
        String centerPlaceId = rs.getString("center_place_id");
        if (centerPlaceId != null && !centerPlaceId.isEmpty()) {
            n.put("centerPlaceId", centerPlaceId);
        }
        n.set("classTypes", readArray(rs.getString("class_types_json")));
        n.set("activityAreas", readArray(rs.getString("activity_areas_json")));
        n.set("degrees", objectMapper.createArrayNode());
        n.set("certificates", objectMapper.createArrayNode());
        n.set("careers", objectMapper.createArrayNode());
        n.set("programs", objectMapper.createArrayNode());
        n.set("reviews", objectMapper.createArrayNode());
        return n;
    }

    private ArrayNode readArray(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                return (ArrayNode) node;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return objectMapper.createArrayNode();
    }
}
