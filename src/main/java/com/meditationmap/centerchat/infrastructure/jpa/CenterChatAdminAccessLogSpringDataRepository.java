package com.meditationmap.centerchat.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CenterChatAdminAccessLogSpringDataRepository
        extends JpaRepository<CenterChatAdminAccessLogJpaEntity, Long> {}
