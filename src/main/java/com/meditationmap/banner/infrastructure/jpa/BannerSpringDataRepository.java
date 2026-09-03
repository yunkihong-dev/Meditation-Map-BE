package com.meditationmap.banner.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerSpringDataRepository extends JpaRepository<BannerJpaEntity, String> {}
