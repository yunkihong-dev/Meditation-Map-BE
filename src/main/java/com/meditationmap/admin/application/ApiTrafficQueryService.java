package com.meditationmap.admin.application;

import com.meditationmap.admin.application.AdminMetricsSeriesBuilder.AdminMetricsSeries;
import com.meditationmap.admin.infrastructure.jpa.HttpTrafficUriDailyJpaEntity;
import com.meditationmap.admin.infrastructure.jpa.HttpTrafficUriDailySpringDataRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiTrafficQueryService {

    private final HttpTrafficUriDailySpringDataRepository repository;

    public ApiTrafficSeriesBundle endpointSeries(String granularity, int limit, int top) {
        int safeLimit = Math.min(120, Math.max(1, limit));
        int safeTop = Math.min(30, Math.max(1, top));
        LocalDate end = LocalDate.now();
        LocalDate start =
                "month".equalsIgnoreCase(granularity)
                        ? YearMonth.now().minusMonths(safeLimit - 1L).atDay(1)
                        : end.minusDays(safeLimit - 1L);

        List<HttpTrafficUriDailyJpaEntity> rows =
                repository.findByTrafficDateBetweenOrderByTrafficDateAscUriAsc(start, end);
        List<String> topUris =
                repository.sumByUriBetween(start, end).stream()
                        .limit(safeTop)
                        .map(HttpTrafficUriDailySpringDataRepository.UriTotalProjection::getUri)
                        .toList();

        List<EndpointTrafficSeries> endpoints = new ArrayList<>();
        for (String uri : topUris) {
            endpoints.add(buildEndpointSeries(uri, granularity, safeLimit, rows));
        }
        return new ApiTrafficSeriesBundle(granularity, safeLimit, endpoints);
    }

    private EndpointTrafficSeries buildEndpointSeries(
            String uri, String granularity, int limit, List<HttpTrafficUriDailyJpaEntity> rows) {
        List<AdminMetricsSeriesBuilder.DailyCountRow> uriRows =
                rows.stream()
                        .filter(row -> uri.equals(row.getUri()))
                        .map(
                                row ->
                                        new AdminMetricsSeriesBuilder.DailyCountRow(
                                                row.getTrafficDate(), row.getRequestCount()))
                        .toList();

        AdminMetricsSeries series;
        if ("month".equalsIgnoreCase(granularity)) {
            YearMonth endMonth = YearMonth.now();
            YearMonth startMonth = endMonth.minusMonths(limit - 1L);
            Map<YearMonth, Long> grouped =
                    AdminMetricsSeriesBuilder.aggregateMonthly(startMonth, endMonth, uriRows);
            series =
                    AdminMetricsSeriesBuilder.monthlySeries(
                            "month", limit, month -> grouped.getOrDefault(month, 0L));
        } else {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(limit - 1L);
            Map<LocalDate, Long> counts = AdminMetricsSeriesBuilder.toDailyMap(start, end, uriRows);
            series =
                    AdminMetricsSeriesBuilder.dailySeries(
                            "day", limit, date -> counts.getOrDefault(date, 0L));
        }
        return new EndpointTrafficSeries(uri, series);
    }

    public record EndpointTrafficSeries(String uri, AdminMetricsSeries series) {}

    public record ApiTrafficSeriesBundle(
            String granularity, int limit, List<EndpointTrafficSeries> endpoints) {}
}
