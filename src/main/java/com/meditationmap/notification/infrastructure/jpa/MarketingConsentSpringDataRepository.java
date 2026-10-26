package com.meditationmap.notification.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingConsentSpringDataRepository
        extends JpaRepository<MarketingConsentJpaEntity, String> {}
