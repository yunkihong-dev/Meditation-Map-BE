package com.meditationmap.place.domain;

import com.meditationmap.region.domain.RegionId;
import java.util.List;
import java.util.Optional;

public interface PlaceRepository {

    List<Place> findAll();

    List<Place> findByRegionId(RegionId regionId);

    Optional<Place> findById(PlaceId id);
}
