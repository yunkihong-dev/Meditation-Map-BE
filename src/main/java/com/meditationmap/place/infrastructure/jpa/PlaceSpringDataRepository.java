package com.meditationmap.place.infrastructure.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceSpringDataRepository extends JpaRepository<PlaceJpaEntity, String> {

    List<PlaceJpaEntity> findByRegionId(String regionId);
}
