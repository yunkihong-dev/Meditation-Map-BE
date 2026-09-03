package com.meditationmap.banner.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.banner.infrastructure.jpa.BannerJpaEntity;
import com.meditationmap.banner.infrastructure.jpa.BannerSpringDataRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BannerQueryService {

    private final BannerSpringDataRepository bannerRepository;

    /**
     * 공개용 — 지금 게시 중인 배너만, 관리자가 정한 순서대로.
     *
     * 캐시 키에 오늘 날짜를 넣습니다. 날짜가 바뀌면 키도 바뀌므로,
     * 예약해 둔 배너가 시작일에 캐시 때문에 안 뜨는 일이 없습니다.
     */
    @Cacheable(value = "banners", key = "T(com.meditationmap.banner.application.BannerVisibility).todayKst().toString()")
    public List<JsonNode> listVisible() {
        LocalDate today = BannerVisibility.todayKst();
        return bannerRepository.findAll().stream()
                .map(BannerJpaEntity::getPayload)
                .filter(payload -> BannerVisibility.isVisible(payload, today))
                .sorted(Comparator.comparingInt(BannerVisibility::sortOrder))
                .toList();
    }
}
