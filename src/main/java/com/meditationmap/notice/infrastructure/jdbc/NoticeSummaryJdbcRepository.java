package com.meditationmap.notice.infrastructure.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 공지 목록: payload 전문 대신 카드/목록에 필요한 필드만 조회합니다. */
@Repository
@RequiredArgsConstructor
public class NoticeSummaryJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<JsonNode> listSummaries() {
        String sql =
                """
                SELECT id,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.category')), '') AS category,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.title')), '') AS title,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.date')), '') AS date,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.summary')), '') AS summary
                FROM notices
                ORDER BY id DESC
                """;
        return jdbc.query(sql, EmptySqlParameterSource.INSTANCE, (rs, rowNum) -> mapRow(rs));
    }

    private JsonNode mapRow(ResultSet rs) throws SQLException {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("id", rs.getString("id"));
        n.put("category", rs.getString("category"));
        n.put("title", rs.getString("title"));
        n.put("date", rs.getString("date"));
        n.put("summary", rs.getString("summary"));
        return n;
    }
}
