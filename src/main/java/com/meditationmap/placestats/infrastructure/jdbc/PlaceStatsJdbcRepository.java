package com.meditationmap.placestats.infrastructure.jdbc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 집계 표 upsert·조회. 행 하나를 읽고 쓰는 왕복 대신 MySQL upsert 한 문장으로 경쟁 없이 더한다. */
@Repository
@RequiredArgsConstructor
public class PlaceStatsJdbcRepository {

    private final JdbcTemplate jdbc;

    /** @return 처음 본 조회자면 true */
    public boolean markViewed(String placeId, LocalDate day, String viewerHash) {
        return jdbc.update(
                        "INSERT IGNORE INTO place_view_dedup (place_id, stat_date, viewer_hash) VALUES (?, ?, ?)",
                        placeId,
                        day,
                        viewerHash)
                == 1;
    }

    public void incrementViews(String placeId, LocalDate day) {
        jdbc.update(
                "INSERT INTO place_view_daily (place_id, stat_date, view_count) VALUES (?, ?, 1)"
                        + " ON DUPLICATE KEY UPDATE view_count = view_count + 1",
                placeId,
                day);
    }

    public void addBookmarkDelta(String placeId, LocalDate day, int added, int removed) {
        jdbc.update(
                "INSERT INTO place_bookmark_daily (place_id, stat_date, added_count, removed_count) VALUES (?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE added_count = added_count + VALUES(added_count),"
                        + " removed_count = removed_count + VALUES(removed_count)",
                placeId,
                day,
                added,
                removed);
    }

    public int deleteDedupBefore(LocalDate day) {
        return jdbc.update("DELETE FROM place_view_dedup WHERE stat_date < ?", day);
    }

    /** [from, to] 일별 조회수. 없는 날은 맵에 없다. */
    public Map<LocalDate, Long> viewsByDay(String placeId, LocalDate from, LocalDate to) {
        Map<LocalDate, Long> out = new HashMap<>();
        jdbc.query(
                "SELECT stat_date, view_count FROM place_view_daily WHERE place_id = ? AND stat_date BETWEEN ? AND ?",
                rs -> {
                    out.put(rs.getObject("stat_date", LocalDate.class), rs.getLong("view_count"));
                },
                placeId,
                from,
                to);
        return out;
    }

    /** [from, to] 일별 북마크 증감 (added, removed). */
    public Map<LocalDate, long[]> bookmarksByDay(String placeId, LocalDate from, LocalDate to) {
        Map<LocalDate, long[]> out = new HashMap<>();
        jdbc.query(
                "SELECT stat_date, added_count, removed_count FROM place_bookmark_daily"
                        + " WHERE place_id = ? AND stat_date BETWEEN ? AND ?",
                rs -> {
                    out.put(
                            rs.getObject("stat_date", LocalDate.class),
                            new long[] {rs.getLong("added_count"), rs.getLong("removed_count")});
                },
                placeId,
                from,
                to);
        return out;
    }

    public long sumViews(String placeId, LocalDate from, LocalDate to) {
        Long v =
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(view_count), 0) FROM place_view_daily"
                                + " WHERE place_id = ? AND stat_date BETWEEN ? AND ?",
                        Long.class,
                        placeId,
                        from,
                        to);
        return v == null ? 0 : v;
    }

    /** 기록이 시작된 첫 날(조회·북마크 중 이른 쪽). 없으면 null. */
    public LocalDate firstDay(String placeId) {
        LocalDate v = minDate("SELECT MIN(stat_date) FROM place_view_daily WHERE place_id = ?", placeId);
        LocalDate b = minDate("SELECT MIN(stat_date) FROM place_bookmark_daily WHERE place_id = ?", placeId);
        if (v == null) {
            return b;
        }
        return b == null || v.isBefore(b) ? v : b;
    }

    private LocalDate minDate(String sql, String placeId) {
        return jdbc.queryForObject(sql, (rs, i) -> rs.getObject(1, LocalDate.class), placeId);
    }

    /** 현재 북마크 수(member_favorite_places 기준). */
    public long currentBookmarks(String placeId) {
        Long v =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM member_favorite_places WHERE place_id = ?", Long.class, placeId);
        return v == null ? 0 : v;
    }
}
