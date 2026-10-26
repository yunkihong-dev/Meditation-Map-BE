package com.meditationmap.centerchat.infrastructure.jdbc;

import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 안 읽은 수·미답변 수 집계. 안 읽은 메시지 = 상대 쪽이 보낸 메시지 중 id > 내 쪽 last_read(채팅 C0). 삭제된 메시지도 센다(화면에
 * '삭제된 메시지' 로 한 줄 남기 때문 — C9).
 */
@Repository
@RequiredArgsConstructor
public class CenterChatJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static String readColumn(SenderRole viewer) {
        return viewer == SenderRole.CUSTOMER ? "t.customer_last_read_message_id" : "t.owner_last_read_message_id";
    }

    public Map<Long, Long> unreadByThread(Collection<Long> threadIds, SenderRole viewer) {
        Map<Long, Long> out = new HashMap<>();
        if (threadIds.isEmpty()) {
            return out;
        }
        String sql =
                "SELECT m.thread_id, COUNT(*) c FROM center_chat_messages m JOIN center_chat_threads t ON t.id = m.thread_id"
                        + " WHERE m.thread_id IN (:ids) AND m.sender_role = :other AND m.id > COALESCE(" + readColumn(viewer) + ", 0)"
                        + " GROUP BY m.thread_id";
        jdbc.query(
                sql,
                new MapSqlParameterSource().addValue("ids", threadIds).addValue("other", viewer.other().name()),
                rs -> {
                    out.put(rs.getLong(1), rs.getLong(2));
                });
        return out;
    }

    /** 고객: [안 읽은 메시지가 있는 대화 수, 안 읽은 메시지 수]. BLOCKED 포함. */
    public long[] customerUnread(String customerId) {
        String sql =
                "SELECT COUNT(DISTINCT m.thread_id), COUNT(*) FROM center_chat_messages m JOIN center_chat_threads t ON t.id = m.thread_id"
                        + " WHERE t.customer_member_id = :c AND m.sender_role = 'OWNER' AND m.id > COALESCE(t.customer_last_read_message_id, 0)";
        return jdbc.queryForObject(sql, Map.of("c", customerId), (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)});
    }

    /** 오너: [미답변 대화 수, 안 읽은 대화 수, 안 읽은 메시지 수] — placeIds 범위. */
    public long[] ownerSummary(Collection<String> placeIds) {
        if (placeIds.isEmpty()) {
            return new long[3];
        }
        MapSqlParameterSource p = new MapSqlParameterSource("places", placeIds);
        Long unanswered =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM center_chat_threads t WHERE t.place_id IN (:places) AND t.status = 'OPEN'"
                                + " AND t.last_sender_role = 'CUSTOMER'",
                        p,
                        Long.class);
        long[] unread =
                jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT m.thread_id), COUNT(*) FROM center_chat_messages m JOIN center_chat_threads t"
                                + " ON t.id = m.thread_id WHERE t.place_id IN (:places) AND m.sender_role = 'CUSTOMER'"
                                + " AND m.id > COALESCE(t.owner_last_read_message_id, 0)",
                        p,
                        (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)});
        return new long[] {unanswered == null ? 0 : unanswered, unread[0], unread[1]};
    }
}
