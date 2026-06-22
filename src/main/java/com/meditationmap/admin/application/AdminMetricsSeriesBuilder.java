package com.meditationmap.admin.application;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class AdminMetricsSeriesBuilder {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN);

    private AdminMetricsSeriesBuilder() {}

    public static AdminMetricsSeries dailySeries(
            String granularity, int limit, Function<LocalDate, Long> countForDate) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(limit - 1L);
        List<AdminMetricsPoint> points = new ArrayList<>(limit);
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            points.add(
                    new AdminMetricsPoint(
                            date.toString(), formatDayLabel(date), countForDate.apply(date)));
        }
        return buildSeries(granularity, limit, points);
    }

    public static AdminMetricsSeries monthlySeries(
            String granularity, int limit, Function<YearMonth, Long> countForMonth) {
        YearMonth endMonth = YearMonth.now();
        YearMonth startMonth = endMonth.minusMonths(limit - 1L);
        List<AdminMetricsPoint> points = new ArrayList<>(limit);
        for (YearMonth month = startMonth; !month.isAfter(endMonth); month = month.plusMonths(1)) {
            points.add(
                    new AdminMetricsPoint(
                            month.toString(),
                            MONTH_LABEL.format(month.atDay(1)),
                            countForMonth.apply(month)));
        }
        return buildSeries(granularity, limit, points);
    }

    public static AdminMetricsSeries buildSeries(
            String granularity, int limit, List<AdminMetricsPoint> points) {
        long total = points.stream().mapToLong(AdminMetricsPoint::count).sum();
        long peak = points.stream().mapToLong(AdminMetricsPoint::count).max().orElse(0L);
        long average = points.isEmpty() ? 0L : Math.round((double) total / points.size());
        return new AdminMetricsSeries(granularity, limit, total, average, peak, points);
    }

    public static Map<LocalDate, Long> toDailyMap(
            LocalDate start, LocalDate end, List<DailyCountRow> rows) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (DailyCountRow row : rows) {
            if (!row.date().isBefore(start) && !row.date().isAfter(end)) {
                counts.put(row.date(), row.count());
            }
        }
        return counts;
    }

    public static Map<YearMonth, Long> aggregateMonthly(
            YearMonth startMonth, YearMonth endMonth, List<DailyCountRow> rows) {
        Map<YearMonth, Long> grouped = new LinkedHashMap<>();
        for (YearMonth month = startMonth; !month.isAfter(endMonth); month = month.plusMonths(1)) {
            grouped.put(month, 0L);
        }
        for (DailyCountRow row : rows) {
            YearMonth month = YearMonth.from(row.date());
            if (!month.isBefore(startMonth) && !month.isAfter(endMonth)) {
                grouped.merge(month, row.count(), Long::sum);
            }
        }
        return grouped;
    }

    private static String formatDayLabel(LocalDate date) {
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    public record DailyCountRow(LocalDate date, long count) {}

    public record AdminMetricsPoint(String key, String label, long count) {}

    public record AdminMetricsSeries(
            String granularity,
            int limit,
            long total,
            long average,
            long peak,
            List<AdminMetricsPoint> points) {}
}
