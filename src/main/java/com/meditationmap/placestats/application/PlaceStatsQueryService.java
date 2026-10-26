package com.meditationmap.placestats.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meditationmap.placestats.domain.StatsRange;
import com.meditationmap.placestats.infrastructure.jdbc.PlaceStatsJdbcRepository;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 오너 대시보드 통계 (GET /me/places/{id}/stats, 계약 C6). 권한 확인은 호출자(OwnerPlaceController)가 먼저 한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceStatsQueryService {

    private final PlaceStatsJdbcRepository stats;
    private final Clock clock;

    public PlaceStats stats(String placeId, String rangeRaw) {
        StatsRange range = StatsRange.parse(rangeRaw);
        if (range == null) {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
        LocalDate today = LocalDate.now(clock.withZone(PlaceViewRecorder.KST));
        LocalDate from;
        if (range == StatsRange.ALL) {
            LocalDate first = stats.firstDay(placeId);
            from = first == null || first.isAfter(today) ? today : first;
        } else {
            from = range.from(today);
        }

        Map<LocalDate, Long> views = stats.viewsByDay(placeId, from, today);
        Map<LocalDate, long[]> marks = stats.bookmarksByDay(placeId, from, today);
        long total = views.values().stream().mapToLong(Long::longValue).sum();
        long added = marks.values().stream().mapToLong(a -> a[0]).sum();
        long removed = marks.values().stream().mapToLong(a -> a[1]).sum();

        Long previous = null;
        Double rate = null;
        if (range.hasPrevious()) {
            previous = stats.sumViews(placeId, range.previousFrom(today), range.previousTo(today));
            rate = StatsRange.changeRate(total, previous);
        }

        String granularity = range == StatsRange.ALL ? "MONTH" : "DAY";
        List<Point> series =
                range == StatsRange.ALL ? monthly(from, today, views, marks) : daily(from, today, views, marks);

        return new PlaceStats(
                placeId,
                range.code(),
                "Asia/Seoul",
                from.toString(),
                today.toString(),
                new Views(total, previous, rate),
                new Bookmarks(stats.currentBookmarks(placeId), added, removed, added - removed),
                granularity,
                series);
    }

    static List<Point> daily(LocalDate from, LocalDate to, Map<LocalDate, Long> views, Map<LocalDate, long[]> marks) {
        List<Point> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            long[] m = marks.getOrDefault(d, new long[2]);
            out.add(new Point(d.toString(), views.getOrDefault(d, 0L), m[0], m[1]));
        }
        return out;
    }

    static List<Point> monthly(LocalDate from, LocalDate to, Map<LocalDate, Long> views, Map<LocalDate, long[]> marks) {
        List<Point> out = new ArrayList<>();
        for (YearMonth ym = YearMonth.from(from); !ym.isAfter(YearMonth.from(to)); ym = ym.plusMonths(1)) {
            final YearMonth cur = ym;
            long v = views.entrySet().stream().filter(e -> YearMonth.from(e.getKey()).equals(cur)).mapToLong(Map.Entry::getValue).sum();
            long a = marks.entrySet().stream().filter(e -> YearMonth.from(e.getKey()).equals(cur)).mapToLong(e -> e.getValue()[0]).sum();
            long r = marks.entrySet().stream().filter(e -> YearMonth.from(e.getKey()).equals(cur)).mapToLong(e -> e.getValue()[1]).sum();
            out.add(new Point(cur.toString(), v, a, r));
        }
        return out;
    }

    /**
     * @param changeRate 직전 기간 대비 %(소수 1자리). 직전이 0 이거나 ALL 이면 null
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Views(long total, Long previousTotal, Double changeRate) {}

    /**
     * @param current 지금 북마크한 회원 수(기간 무관)
     * @param added 기간 중 새 북마크 / removed 기간 중 해제 / net = added - removed
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Bookmarks(long current, long added, long removed, long net) {}

    /** @param date DAY 면 YYYY-MM-DD, MONTH 면 YYYY-MM */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Point(String date, long views, long bookmarksAdded, long bookmarksRemoved) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PlaceStats(
            String placeId,
            String range,
            String timezone,
            String from,
            String to,
            Views views,
            Bookmarks bookmarks,
            String granularity,
            List<Point> series) {}
}
