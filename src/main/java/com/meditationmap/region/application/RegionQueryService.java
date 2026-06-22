package com.meditationmap.region.application;

import com.meditationmap.region.domain.Region;
import com.meditationmap.region.domain.RegionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionQueryService {

    private final RegionRepository regionRepository;

    @Cacheable(value = "regions")
    public List<Region> listRegions() {
        return regionRepository.findAllOrderByName();
    }
}
