package com.meditationmap.expert.infrastructure.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpertSpringDataRepository extends JpaRepository<ExpertJpaEntity, String> {

    Optional<ExpertJpaEntity> findByOwnerMemberId(String ownerMemberId);
}
