package com.meditationmap.admin.application;

import com.meditationmap.expert.infrastructure.jpa.ExpertSpringDataRepository;
import com.meditationmap.identity.infrastructure.jpa.MemberSpringDataRepository;
import com.meditationmap.notice.infrastructure.jpa.NoticeSpringDataRepository;
import com.meditationmap.place.infrastructure.jpa.PlaceSpringDataRepository;
import com.meditationmap.region.domain.KoreaRegions;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMetricsService {

    private final MeterRegistry meterRegistry;
    private final PlaceSpringDataRepository placeRepo;
    private final ExpertSpringDataRepository expertRepo;
    private final NoticeSpringDataRepository noticeRepo;
    private final MemberSpringDataRepository memberRepo;

    public AdminTrafficSnapshot trafficSnapshot() {
        Map<String, Long> byUri = new HashMap<>();
        Search.in(meterRegistry)
                .name("http.server.requests")
                .timers()
                .forEach(
                        timer -> {
                            long count = (long) timer.count();
                            String uri = timer.getId().getTag("uri");
                            if (uri == null) {
                                uri = "(unknown)";
                            }
                            byUri.merge(uri, count, Long::sum);
                        });
        long httpTotal = byUri.values().stream().mapToLong(Long::longValue).sum();

        List<EndpointHit> top =
                byUri.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(12)
                        .map(e -> new EndpointHit(e.getKey(), e.getValue()))
                        .toList();

        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

        return new AdminTrafficSnapshot(
                httpTotal,
                placeRepo.count(),
                expertRepo.count(),
                KoreaRegions.COUNT,
                noticeRepo.count(),
                memberRepo.count(),
                heap.getUsed(),
                heap.getMax() > 0 ? heap.getMax() : heap.getCommitted(),
                new ArrayList<>(top));
    }

    public record EndpointHit(String uri, long count) {}

    public record AdminTrafficSnapshot(
            long totalHttpRequests,
            long placesCount,
            long expertsCount,
            long regionsCount,
            long noticesCount,
            long membersCount,
            long jvmHeapUsedBytes,
            long jvmHeapMaxBytes,
            List<EndpointHit> topEndpoints) {}
}
