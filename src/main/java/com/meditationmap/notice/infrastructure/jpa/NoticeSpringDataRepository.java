package com.meditationmap.notice.infrastructure.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeSpringDataRepository extends JpaRepository<NoticeJpaEntity, String> {

    List<NoticeJpaEntity> findAllByOrderByIdDesc();
}
