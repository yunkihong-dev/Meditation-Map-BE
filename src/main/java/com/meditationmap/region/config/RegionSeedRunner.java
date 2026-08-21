package com.meditationmap.region.config;

import com.meditationmap.region.domain.KoreaRegions;
import com.meditationmap.region.infrastructure.jpa.RegionJpaEntity;
import com.meditationmap.region.infrastructure.jpa.RegionSpringDataRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기동 시 시·도 17개가 regions 테이블에 있는지 보장합니다.
 *
 * <p>지역 목록이 비어 있으면 지도의 지역 칩과 지역별 목록이 통째로 사라집니다. 장소는 멀쩡히
 * 등록돼 있는데 화면에는 아무것도 안 나오는, 원인을 찾기 어려운 형태로 깨집니다. 새 DB 를 만들
 * 때마다 사람이 INSERT 를 기억해야 하는 상태로 두지 않기 위해 기동 시점에 맞춥니다.
 *
 * <p>이름·slug 가 바뀐 경우도 코드 쪽 값으로 되돌립니다. 운영자가 바꿀 수 있는 데이터가 아니라
 * 코드가 단일 기준이기 때문입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionSeedRunner implements ApplicationRunner {

    private final RegionSpringDataRepository regions;
    private final ObjectProvider<CacheManager> cacheManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, RegionJpaEntity> existing =
                regions.findAll().stream()
                        .collect(Collectors.toMap(RegionJpaEntity::getId, Function.identity()));

        List<RegionJpaEntity> changed =
                KoreaRegions.all().stream()
                        .map(seed -> reconcile(existing.get(seed.id()), seed))
                        .filter(java.util.Objects::nonNull)
                        .toList();

        if (changed.isEmpty()) {
            return;
        }
        regions.saveAll(changed);
        evictRegionsCache();
        log.info("지역 시드 반영 {}건 (전체 {}개)", changed.size(), KoreaRegions.COUNT);
    }

    /** 새로 넣거나 값이 어긋난 행만 돌려주고, 이미 같으면 null 을 돌려 쓰기를 건너뜁니다. */
    private RegionJpaEntity reconcile(RegionJpaEntity current, KoreaRegions.Seed seed) {
        if (current == null) {
            RegionJpaEntity created = new RegionJpaEntity();
            created.setId(seed.id());
            created.setName(seed.name());
            created.setSlug(seed.slug());
            created.setSortOrder(seed.sortOrder());
            return created;
        }
        boolean same =
                seed.name().equals(current.getName())
                        && seed.slug().equals(current.getSlug())
                        && seed.sortOrder() == current.getSortOrder();
        if (same) {
            return null;
        }
        current.setName(seed.name());
        current.setSlug(seed.slug());
        current.setSortOrder(seed.sortOrder());
        return current;
    }

    /**
     * 목록이 비어 있는 동안 캐시에 빈 배열이 올라갔을 수 있습니다. TTL(기본 10분)을 기다리면 배포
     * 직후 사용자에게는 여전히 지역이 없는 화면이 보이므로 시드가 바뀐 김에 비웁니다.
     */
    private void evictRegionsCache() {
        CacheManager manager = cacheManager.getIfAvailable();
        if (manager == null) {
            return;
        }
        var cache = manager.getCache("regions");
        if (cache != null) {
            cache.clear();
        }
    }
}
