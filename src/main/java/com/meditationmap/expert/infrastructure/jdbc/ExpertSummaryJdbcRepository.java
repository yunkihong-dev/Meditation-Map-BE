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
