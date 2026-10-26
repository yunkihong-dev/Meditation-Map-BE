package com.meditationmap.placestats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.meditationmap.placestats.infrastructure.jdbc.PlaceStatsJdbcRepository;
import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 통계 응답 조립 — 0 채움 시계열, 직전 0 이면 증감률 null, ALL 은 월 단위·비교 없음, 잘못된 range 400. */
@ExtendWith(MockitoExtension.class)
class PlaceStatsQueryServiceTest {

    @Mock private PlaceStatsJdbcRepository repo;


    private PlaceStatsQueryService svc() {
        return new PlaceStatsQueryService(repo, Clock.fixed(Instant.parse("2026-10-06T15:30:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("1W: KST 오늘(10-07) 포함 7일, 빈 날은 0, 직전 0 이면 changeRate null")
    void 주간() {
        LocalDate from = LocalDate.parse("2026-10-01"), to = LocalDate.parse("2026-10-07");
        given(repo.viewsByDay("12", from, to)).willReturn(Map.of(LocalDate.parse("2026-10-03"), 4L, to, 2L));
        given(repo.bookmarksByDay("12", from, to)).willReturn(Map.of(to, new long[] {3, 1}));
        given(repo.sumViews("12", LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-30"))).willReturn(0L);
        given(repo.currentBookmarks("12")).willReturn(342L);

        PlaceStatsQueryService.PlaceStats s = svc().stats("12", "1W");

        assertThat(s.from()).isEqualTo("2026-10-01");
        assertThat(s.to()).isEqualTo("2026-10-07");
        assertThat(s.granularity()).isEqualTo("DAY");
        assertThat(s.series()).hasSize(7);
        assertThat(s.series().get(0).views()).isZero();
        assertThat(s.series().get(2).views()).isEqualTo(4);
        assertThat(s.views().total()).isEqualTo(6);
        assertThat(s.views().previousTotal()).isZero();
        assertThat(s.views().changeRate()).isNull();
        assertThat(s.bookmarks().current()).isEqualTo(342);
        assertThat(s.bookmarks().net()).isEqualTo(2);
    }

    @Test
    @DisplayName("ALL: 첫 기록일부터 월 단위, 비교 없음(previousTotal·changeRate null)")
    void 전체() {
        LocalDate first = LocalDate.parse("2026-08-20"), to = LocalDate.parse("2026-10-07");
        given(repo.firstDay("12")).willReturn(first);
        given(repo.viewsByDay("12", first, to))
                .willReturn(Map.of(first, 1L, LocalDate.parse("2026-09-01"), 5L, LocalDate.parse("2026-09-30"), 5L));
        given(repo.bookmarksByDay("12", first, to)).willReturn(Map.of());
        given(repo.currentBookmarks("12")).willReturn(0L);

        PlaceStatsQueryService.PlaceStats s = svc().stats("12", "ALL");

        assertThat(s.granularity()).isEqualTo("MONTH");
        assertThat(s.series()).extracting(PlaceStatsQueryService.Point::date).containsExactly("2026-08", "2026-09", "2026-10");
        assertThat(s.series()).extracting(PlaceStatsQueryService.Point::views).containsExactly(1L, 10L, 0L);
        assertThat(s.views().previousTotal()).isNull();
        assertThat(s.views().changeRate()).isNull();
    }

    @Test
    @DisplayName("기록이 하나도 없으면 ALL 은 오늘 하루짜리 0 시계열")
    void 기록_없음() {
        LocalDate today = LocalDate.parse("2026-10-07");
        given(repo.firstDay("12")).willReturn(null);
        given(repo.viewsByDay("12", today, today)).willReturn(Map.of());
        given(repo.bookmarksByDay("12", today, today)).willReturn(Map.of());

        PlaceStatsQueryService.PlaceStats s = svc().stats("12", "ALL");
        assertThat(s.series()).hasSize(1);
        assertThat(s.views().total()).isZero();
    }

    @Test
    @DisplayName("모르는 range 는 400 INVALID_PARAMETER")
    void 잘못된_range() {
        assertThatThrownBy(() -> svc().stats("12", "2Y"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PARAMETER);
    }
}
