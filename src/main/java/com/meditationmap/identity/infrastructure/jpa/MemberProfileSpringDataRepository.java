package com.meditationmap.identity.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberProfileSpringDataRepository
        extends JpaRepository<MemberProfileJpaEntity, String> {}
