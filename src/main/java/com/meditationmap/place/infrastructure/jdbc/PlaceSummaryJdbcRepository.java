package com.meditationmap.place.infrastructure.jdbc;

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
 * 장소 목록은 JSON 전체 컬럼을 읽지 않고 필요한 경로만 SELECT합니다 (한 번의 쿼리, N+1 없음).
 */
@Repository
@RequiredArgsConstructor
public class PlaceSummaryJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<JsonNode> listByRegionId(String regionIdRaw) {
        String sql =
                """
                SELECT id,
                       region_id,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.name')), '') AS name,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.shortDescription')), '') AS short_description,
                       LEFT(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.description')), ''), 1200) AS description,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.address')), '') AS address,
                       -- 지도 마커가 쓰는 좌표. 없으면 NULL 로 두고 프런트가 지역 근사 좌표로 떨어진다.
                       CAST(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.lat')) AS DECIMAL(10, 7)) AS lat,
                       CAST(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.lng')) AS DECIMAL(10, 7)) AS lng,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.thumbnailUrl')), '') AS thumbnail_url,
                       JSON_EXTRACT(`data`, '$.hashtags') AS hashtags_json,
                       JSON_EXTRACT(`data`, '$.themes') AS themes_json,
                       IF(JSON_EXTRACT(`data`, '$.hasTempleStay') = CAST('true' AS JSON), 1, 0) AS has_temple_stay,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.duration')), '') AS duration,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.admissionFee')), '') AS admission_fee,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.venueKind')), '') AS venue_kind,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.organization.name')), '') AS org_name,
                       COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.viewCount')) AS UNSIGNED), 0) AS view_count,
                       COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.rating')) AS DECIMAL(3, 2)), 0) AS rating,
                       COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.reviewCount')) AS UNSIGNED), 0) AS review_count,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.externalLink')), '') AS external_link
                FROM places
                -- 숨김 처리된 장소는 공개 목록에서 제외한다.
                -- 1) 기존 OR 절은 반드시 괄호로 묶는다. 빼면 SQL 이 `A OR (B AND C)` 로 읽어
                --    regionId=all(기본값) 일 때 hidden 필터가 통째로 무시된다.
                -- 2) hidden 키가 없는 행(운영 데이터 대다수)에서 JSON_EXTRACT 는 SQL NULL 을
                --    돌려주므로 COALESCE 로 'false' 로 접지 않으면 그 행들이 전부 사라진다.
                -- 3) JSON_UNQUOTE 로 문자열화해서 비교하면 boolean true·문자열 "true"·숫자 1 을
                --    모두 걸러 낸다. JSON 비교(= CAST('true' AS JSON))는 문자열/숫자 형태를 놓친다.
                WHERE (:regionId = 'all' OR region_id = :regionId)
                  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`data`, '$.hidden')), 'false') NOT IN ('true', '1')
                ORDER BY id
                """;
        return jdbc.query(
                sql,
                new MapSqlParameterSource("regionId", regionIdRaw),
                (rs, rowNum) -> mapRow(rs));
    }

    private JsonNode mapRow(ResultSet rs) throws SQLException {
        ObjectNode n = objectMapper.createObjectNode();
        String id = rs.getString("id");
        n.put("id", id);
        n.put("regionId", rs.getString("region_id"));
        n.put("name", rs.getString("name"));
        n.put("shortDescription", rs.getString("short_description"));
        n.put("description", rs.getString("description"));
        n.put("address", rs.getString("address"));
        /*
         * 좌표는 있을 때만 넣는다. 0.0 으로 채워 버리면 프런트가 "좌표 있음" 으로 보고
         * 아프리카 앞바다에 마커를 찍는다. 없으면 키 자체를 빼서 지역 근사 좌표로 가게 둔다.
         */
        putCoordinate(n, "lat", rs, "lat");
        putCoordinate(n, "lng", rs, "lng");
        n.put("thumbnailUrl", rs.getString("thumbnail_url"));
        n.set("hashtags", readArray(rs.getString("hashtags_json")));
        n.set("themes", readArray(rs.getString("themes_json")));
        n.put("hasTempleStay", rs.getBoolean("has_temple_stay"));
        n.put("duration", rs.getString("duration"));
        n.put("admissionFee", rs.getString("admission_fee"));
        n.put("venueKind", rs.getString("venue_kind"));
        ObjectNode org = objectMapper.createObjectNode();
        org.put("name", rs.getString("org_name"));
        n.set("organization", org);
        n.put("viewCount", rs.getLong("view_count"));
        double ratingVal = rs.getDouble("rating");
        if (rs.wasNull()) {
            n.putNull("rating");
        } else {
            n.put("rating", ratingVal);
        }
        n.put("reviewCount", rs.getLong("review_count"));
        n.put("externalLink", rs.getString("external_link"));
        n.set("programs", objectMapper.createArrayNode());
        n.set("instructors", objectMapper.createArrayNode());
        n.set("detailSections", objectMapper.createArrayNode());
        return n;
    }

    private static void putCoordinate(ObjectNode target, String field, ResultSet rs, String column)
            throws SQLException {
        double value = rs.getDouble(column);
        if (!rs.wasNull() && Double.isFinite(value)) {
            target.put(field, value);
        }
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
