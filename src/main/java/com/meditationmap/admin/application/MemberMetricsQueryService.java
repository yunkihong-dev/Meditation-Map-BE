package com.meditationmap.admin.application;

import com.meditationmap.admin.application.AdminMetricsSeriesBuilder.AdminMetricsSeries;
import com.meditationmap.identity.domain.MemberRole;
import com.meditationmap.identity.infrastructure.jpa.MemberSpringDataRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberMetricsQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberSpringDataRepository memberRepository;

    public MemberMetricsBundle memberSeries(String granularity, int limit) {
        int safeLimit = Math.min(120, Math.max(1, limit));
        AdminMetricsSeries signupSeries;
        AdminMetricsSeries cumulativeSeries;
        if ("month".equalsIgnoreCase(granularity)) {
            signupSeries = monthlySignupSeries(safeLimit);
            cumulativeSeries = monthlyCumulativeSeries(safeLimit);
        } else {
            signupSeries = dailySignupSeries(safeLimit);
            cumulativeSeries = dailyCumulativeSeries(safeLimit);
        }
        long currentTotal = memberRepository.countByRole(MemberRole.MEMBER);
        return new MemberMetricsBundle(
                granularity, safeLimit, currentTotal, signupSeries, cumulativeSeries);
    }

    private AdminMetricsSeries dailySignupSeries(int days) {
        LocalDate end = LocalDate.now(KST);
        LocalDate start = end.minusDays(days - 1L);
        Map<LocalDate, Long> signups = signupsByDay(start, end);
        return AdminMetricsSeriesBuilder.dailySeries(
                "day", days, date -> signups.getOrDefault(date, 0L));
    }

    private AdminMetricsSeries dailyCumulativeSeries(int days) {
        LocalDate end = LocalDate.now(KST);
        LocalDate start = end.minusDays(days - 1L);
        Map<LocalDate, Long> signups = signupsByDay(start, end);
        long baseline =
                memberRepository.countByRoleAndCreatedAtBefore(
                        MemberRole.MEMBER, start.atStartOfDay(KST).toInstant());
        return AdminMetricsSeriesBuilder.dailySeries(
                "day",
                days,
                date -> {
                    long total = baseline;
                    for (LocalDate cursor = start; !cursor.isAfter(date); cursor = cursor.plusDays(1)) {
                        total += signups.getOrDefault(cursor, 0L);
                    }
                    return total;
                });
    }

    private AdminMetricsSeries monthlySignupSeries(int months) {
        YearMonth endMonth = YearMonth.now(KST);
        YearMonth startMonth = endMonth.minusMonths(months - 1L);
        Map<YearMonth, Long> signups = signupsByMonth(startMonth, endMonth);
        return AdminMetricsSeriesBuilder.monthlySeries(
                "month", months, month -> signups.getOrDefault(month, 0L));
    }

    private AdminMetricsSeries monthlyCumulativeSeries(int months) {
        YearMonth endMonth = YearMonth.now(KST);
        YearMonth startMonth = endMonth.minusMonths(months - 1L);
        Map<YearMonth, Long> signups = signupsByMonth(startMonth, endMonth);
        long baseline =
                memberRepository.countByRoleAndCreatedAtBefore(
                        MemberRole.MEMBER, startMonth.atDay(1).atStartOfDay(KST).toInstant());
        return AdminMetricsSeriesBuilder.monthlySeries(
                "month",
                months,
                month -> {
                    long total = baseline;
                    for (YearMonth cursor = startMonth;
                            !cursor.isAfter(month);
                            cursor = cursor.plusMonths(1)) {
                        total += signups.getOrDefault(cursor, 0L);
                    }
                    return total;
                });
    }

    private Map<LocalDate, Long> signupsByDay(LocalDate start, LocalDate end) {
        Instant rangeStart = start.atStartOfDay(KST).toInstant();
        Instant rangeEnd = end.plusDays(1).atStartOfDay(KST).toInstant();
        Map<LocalDate, Long> signups = new HashMap<>();
        for (MemberSpringDataRepository.SignupDayRow row :
                memberRepository.countMemberSignupsByDay(MemberRole.MEMBER.name(), rangeStart, rangeEnd)) {
            signups.put(row.getSignupDate(), row.getSignupCount());
        }
        return signups;
    }

    private Map<YearMonth, Long> signupsByMonth(YearMonth startMonth, YearMonth endMonth) {
        LocalDate start = startMonth.atDay(1);
        LocalDate end = endMonth.atEndOfMonth();
        Map<YearMonth, Long> grouped = new HashMap<>();
        for (Map.Entry<LocalDate, Long> entry : signupsByDay(start, end).entrySet()) {
            grouped.merge(YearMonth.from(entry.getKey()), entry.getValue(), Long::sum);
        }
        return grouped;
    }

    public record MemberMetricsBundle(
            String granularity,
            int limit,
            long currentTotal,
            AdminMetricsSeries signupSeries,
            AdminMetricsSeries cumulativeSeries) {}
}
