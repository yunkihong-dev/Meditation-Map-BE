package com.meditationmap.centerchat.infrastructure.jpa;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CenterChatReportSpringDataRepository extends JpaRepository<CenterChatReportJpaEntity, Long> {

    boolean existsByThreadId(Long threadId);

    /** 엔티티를 올리지 않고 대화 id 만(잠금 순서: 대화 → 신고). */
    @Query("select r.threadId from CenterChatReportJpaEntity r where r.id = :id")
    Optional<Long> findThreadIdById(@Param("id") Long id);

    Optional<CenterChatReportJpaEntity> findByActiveKey(String activeKey);

    long countByThreadIdAndIdNot(Long threadId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CenterChatReportJpaEntity r where r.id = :id")
    Optional<CenterChatReportJpaEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CenterChatReportJpaEntity r where r.threadId = :t and r.status = 'PENDING' order by r.id")
    List<CenterChatReportJpaEntity> pendingForThreadForUpdate(@Param("t") Long threadId);

    @Query(
            "select r from CenterChatReportJpaEntity r where (:status is null or r.status = :status)"
                    + " and (:cursor is null or r.id < :cursor) order by r.id desc")
    List<CenterChatReportJpaEntity> page(@Param("status") String status, @Param("cursor") Long cursor, Pageable page);
}
