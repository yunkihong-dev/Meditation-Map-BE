package com.meditationmap.admin.application;

import com.meditationmap.admin.infrastructure.jpa.HttpTrafficDailySpringDataRepository;
import com.meditationmap.admin.infrastructure.jpa.HttpTrafficUriDailySpringDataRepository;
import java.time.LocalDate;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HttpTrafficRecorder {

    private static final Pattern UUID_SEGMENT = Pattern.compile("/[0-9a-fA-F-]{8,}");
    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("/\\d+");

    private final HttpTrafficDailySpringDataRepository dailyRepository;
    private final HttpTrafficUriDailySpringDataRepository uriDailyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRequest(String rawUri) {
        LocalDate today = LocalDate.now();
        dailyRepository.incrementForDate(today);
        uriDailyRepository.incrementForDateAndUri(today, normalizeUri(rawUri));
    }

    static String normalizeUri(String rawUri) {
        if (rawUri == null || rawUri.isBlank()) {
            return "/";
        }
        String normalized = UUID_SEGMENT.matcher(rawUri).replaceAll("/{id}");
        normalized = NUMERIC_SEGMENT.matcher(normalized).replaceAll("/{id}");
        if (normalized.length() > 200) {
            return normalized.substring(0, 200);
        }
        return normalized;
    }
}
