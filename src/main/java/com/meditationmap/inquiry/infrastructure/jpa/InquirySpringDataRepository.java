package com.meditationmap.inquiry.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InquirySpringDataRepository extends JpaRepository<InquiryJpaEntity, String> {}
