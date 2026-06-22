package com.meditationmap.expert.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpertVerificationSpringDataRepository
        extends JpaRepository<ExpertVerificationJpaEntity, String> {}
