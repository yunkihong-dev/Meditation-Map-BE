package com.meditationmap.interest.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestSpringDataRepository extends JpaRepository<InterestJpaEntity, String> {

    List<InterestJpaEntity> findAllByOrderBySortOrderAscNameAsc();

    List<InterestJpaEntity> findAllByActiveTrueOrderBySortOrderAscNameAsc();

    Optional<InterestJpaEntity> findByName(String name);
}
