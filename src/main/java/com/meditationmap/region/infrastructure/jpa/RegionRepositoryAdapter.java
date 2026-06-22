package com.meditationmap.region.infrastructure.jpa;

import com.meditationmap.region.domain.Region;
import com.meditationmap.region.domain.RegionId;
import com.meditationmap.region.domain.RegionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegionRepositoryAdapter implements RegionRepository {

    private final RegionSpringDataRepository springData;

    @Override
    public List<Region> findAllOrderByName() {
        return springData.findAllByOrderByNameAsc().stream()
                .map(
                        e ->
                                new Region(
                                        RegionId.of(e.getId()),
                                        e.getName(),
                                        e.getSlug(),
                                        e.getSortOrder()))
                .toList();
    }
}
