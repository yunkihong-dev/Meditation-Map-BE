package com.meditationmap.region.infrastructure.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionSpringDataRepository extends JpaRepository<RegionJpaEntity, String> {

    List<RegionJpaEntity> findAllByOrderByNameAsc();
}
