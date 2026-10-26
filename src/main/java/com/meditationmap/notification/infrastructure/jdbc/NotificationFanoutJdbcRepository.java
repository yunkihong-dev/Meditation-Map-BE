package com.meditationmap.notification.infrastructure.jdbc;

import com.meditationmap.identity.application.MemberDirectory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 광고 팬아웃 — 수신자 수만큼 알림 행을 한 문장(INSERT … SELECT)으로 만든다. 회원 수만큼 왕복하지 않는다.
 *
 * <p>대상은 비스태프({@code MEMBER·EXPERT·USER}) 이면서 광고 수신에 동의한 회원뿐이다(D3).
 */
@Repository
@RequiredArgsConstructor
public class NotificationFanoutJdbcRepository {

    private static final String INSERT_SELECT =
            """
            INSERT INTO notifications
              (recipient_member_id, type, title, body, link_url, ref_type, ref_id, broadcast_id, read_at, created_at)
            SELECT u.id, :type, :title, :body, :linkUrl, :refType, :refId, :broadcastId, NULL, :createdAt
            FROM users u
            JOIN member_marketing_consents c ON c.member_id = u.id AND c.push_agreed = TRUE
            WHERE u.role IN (:roles)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** 전체 회원(동의자). @return 만든 행 수 */
    public int insertForAllConsented(FanoutRow row) {
        return jdbc.update(INSERT_SELECT, params(row));
    }

    /** 지정 회원 중 동의자. @return 만든 행 수 */
    public int insertForConsentedMembers(FanoutRow row, Collection<String> memberIds) {
        if (memberIds.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource p = params(row).addValue("ids", memberIds);
        return jdbc.update(INSERT_SELECT + " AND u.id IN (:ids)", p);
    }

    private static MapSqlParameterSource params(FanoutRow row) {
        return new MapSqlParameterSource()
                .addValue("type", row.type())
                .addValue("title", row.title())
                .addValue("body", row.body())
                .addValue("linkUrl", row.linkUrl())
                .addValue("refType", row.refType())
                .addValue("refId", row.refId())
                .addValue("broadcastId", row.broadcastId())
                // Hibernate 는 Instant 를 UTC 벽시계로 DATETIME(6) 에 쓴다. JDBC 로 Timestamp 를 넘기면 JVM 시간대로
                // 바뀌어 JPA 로 쓴 행과 어긋나므로 UTC LocalDateTime 으로 맞춘다.
                .addValue("createdAt", LocalDateTime.ofInstant(row.createdAt(), ZoneOffset.UTC))
                .addValue("roles", MemberDirectory.NON_STAFF_DB_ROLES);
    }

    public record FanoutRow(
            String type,
            String title,
            String body,
            String linkUrl,
            String refType,
            String refId,
            Long broadcastId,
            Instant createdAt) {}
}
