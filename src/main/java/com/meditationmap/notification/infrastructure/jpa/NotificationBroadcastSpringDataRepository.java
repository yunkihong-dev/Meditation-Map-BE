package com.meditationmap.notification.infrastructure.jpa;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationBroadcastSpringDataRepository
        extends JpaRepository<NotificationBroadcastJpaEntity, Long> {

    boolean existsByRequestId(String requestId);

    List<NotificationBroadcastJpaEntity> findAllByOrderByIdDesc(Pageable page);

    List<NotificationBroadcastJpaEntity> findByIdLessThanOrderByIdDesc(Long cursor, Pageable page);
}
