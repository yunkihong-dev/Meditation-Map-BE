package com.meditationmap.admin.application;

import com.meditationmap.admin.application.AdminMetricsSeriesBuilder.AdminMetricsSeries;
import com.meditationmap.admin.infrastructure.jpa.HttpTrafficDailyJpaEntity;
import com.meditationmap.admin.infrastructure.jpa.HttpTrafficDailySpringDataRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HttpTrafficQueryService {

    private final HttpTrafficDailySpringDataRepository repository;

    public AdminMetricsSeries series(String granularity, int limit) {
        int safeLimit = Math.min(120, Math.max(1, limit));
        if ("month".equalsIgnoreCase(granularity)) {
            return monthlySeries(safeLimit);
        }
        return dailySeries(safeLimit);
    }

    private AdminMetricsSeries dailySeries(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        Map<LocalDate, Long> counts = loadCounts(start, end);
        return AdminMetricsSeriesBuilder.dailySeries(
                "day", days, date -> counts.getOrDefault(date, 0L));
    }

    private AdminMetricsSeries monthlySeries(int months) {
        YearMonth endMonth = YearMonth.now();
        YearMonth startMonth = endMonth.minusMonths(months - 1L);
        LocalDate start = startMonth.atDay(1);
        LocalDate end = LocalDate.now();
        Map<YearMonth, Long> grouped =
                AdminMetricsSeriesBuilder.aggregateMonthly(
                        startMonth,
                        endMonth,
                        loadDailyRows(start, end).stream()
                                .map(
                                        row ->
                                                new AdminMetricsSeriesBuilder.DailyCountRow(
                                                        row.getTrafficDate(), row.getRequestCount()))
                                .toList());
        return AdminMetricsSeriesBuilder.monthlySeries(
                "month", months, month -> grouped.getOrDefault(month, 0L));
    }

    private Map<LocalDate, Long> loadCounts(LocalDate start, LocalDate end) {
        return AdminMetricsSeriesBuilder.toDailyMap(
                start,
                end,
                loadDailyRows(start, end).stream()
                        .map(
                                row ->
                                        new AdminMetricsSeriesBuilder.DailyCountRow(
                                                row.getTrafficDate(), row.getRequestCount()))
                        .toList());
    }

    private java.util.List<HttpTrafficDailyJpaEntity> loadDailyRows(LocalDate start, LocalDate end) {
        return repository.findByTrafficDateBetweenOrderByTrafficDateAsc(start, end);
    }
}
