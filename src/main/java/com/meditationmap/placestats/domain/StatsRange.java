package com.meditationmap.placestats.domain;

import java.time.LocalDate;
import java.util.Locale;

/**
 * 통계 기간 (라운드 3 계약 C6). 모두 KST 날짜 기준, 오늘 포함. 집계 단위가 "일" 이라 시간 단위는 없다.
 *
 * <ul>
 *   <li>{@code 1D} 오늘(KST 0시~지금) 합계 · 비교 대상 어제
 *   <li>{@code 1W} 오늘 포함 최근 7일 · 비교 대상 그 앞 7일
 *   <li>{@code 1M} 오늘 포함 최근 30일 · 비교 대상 그 앞 30일 (달력상 "이번 달" 이 아니다)
 *   <li>{@code ALL} 기록이 시작된 첫 날 ~ 오늘 · 비교 없음, 시계열은 월 단위
 * </ul>
 */
public enum StatsRange {
    D1("1D", 1),
    W1("1W", 7),
    M1("1M", 30),
    ALL("ALL", 0);

    private final String code;
    private final int days;

    StatsRange(String code, int days) {
        this.code = code;
        this.days = days;
    }

    public String code() {
        return code;
    }

    public static StatsRange parse(String raw) {
        String v = raw == null || raw.isBlank() ? "1M" : raw.trim().toUpperCase(Locale.ROOT);
        for (StatsRange r : values()) {
            if (r.code.equals(v)) {
                return r;
            }
        }
        return null;
    }

    /** 기간 시작(포함). ALL 은 호출자가 첫 기록일로 정한다. */
    public LocalDate from(LocalDate today) {
        return today.minusDays(days - 1L);
    }

    public LocalDate previousFrom(LocalDate today) {
        return today.minusDays(2L * days - 1);
    }

    public LocalDate previousTo(LocalDate today) {
        return today.minusDays(days);
    }

    public boolean hasPrevious() {
        return this != ALL;
    }

    /**
     * 증감률(%) 소수 첫째 자리 반올림. 직전 기간이 0 이면 null(0 나누기 — "신규" 로 볼지는 화면이 정한다).
     */
    public static Double changeRate(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        double rate = (current - previous) * 100.0 / previous;
        return Math.round(rate * 10.0) / 10.0;
    }
}
