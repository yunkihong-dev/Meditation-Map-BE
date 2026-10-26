package com.meditationmap.placestats.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 기간 정의와 증감률(0 나누기) — 라운드 3 계약 C6. */
class StatsRangeTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-07");

    @Test
    @DisplayName("1D=오늘, 직전=어제 / 1W=오늘 포함 7일, 직전 7일 / 1M=오늘 포함 30일, 직전 30일")
    void 기간_경계() {
        assertThat(StatsRange.D1.from(TODAY)).isEqualTo(TODAY);
        assertThat(StatsRange.D1.previousFrom(TODAY)).isEqualTo(LocalDate.parse("2026-10-06"));
        assertThat(StatsRange.D1.previousTo(TODAY)).isEqualTo(LocalDate.parse("2026-10-06"));

        assertThat(StatsRange.W1.from(TODAY)).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(StatsRange.W1.previousFrom(TODAY)).isEqualTo(LocalDate.parse("2026-09-24"));
        assertThat(StatsRange.W1.previousTo(TODAY)).isEqualTo(LocalDate.parse("2026-09-30"));

        assertThat(StatsRange.M1.from(TODAY)).isEqualTo(LocalDate.parse("2026-09-08"));
        assertThat(StatsRange.M1.previousFrom(TODAY)).isEqualTo(LocalDate.parse("2026-08-09"));
        assertThat(StatsRange.M1.previousTo(TODAY)).isEqualTo(LocalDate.parse("2026-09-07"));
        assertThat(StatsRange.ALL.hasPrevious()).isFalse();
    }

    @Test
    @DisplayName("range 파싱: 기본 1M, 대소문자 무시, 모르는 값은 null(→ 400)")
    void 파싱() {
        assertThat(StatsRange.parse(null)).isEqualTo(StatsRange.M1);
        assertThat(StatsRange.parse("1w")).isEqualTo(StatsRange.W1);
        assertThat(StatsRange.parse("all")).isEqualTo(StatsRange.ALL);
        assertThat(StatsRange.parse("2Y")).isNull();
    }

    @Test
    @DisplayName("증감률: 직전 0 이면 null(0 나누기 없음), 그 외 소수 1자리")
    void 증감률() {
        assertThat(StatsRange.changeRate(5, 0)).isNull();
        assertThat(StatsRange.changeRate(0, 0)).isNull();
        assertThat(StatsRange.changeRate(112, 100)).isEqualTo(12.0);
        assertThat(StatsRange.changeRate(0, 3)).isEqualTo(-100.0);
        assertThat(StatsRange.changeRate(1, 3)).isEqualTo(-66.7);
    }
}
