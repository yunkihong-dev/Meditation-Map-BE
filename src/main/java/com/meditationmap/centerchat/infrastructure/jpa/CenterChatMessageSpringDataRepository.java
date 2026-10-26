package com.meditationmap.centerchat.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CenterChatMessageSpringDataRepository extends JpaRepository<CenterChatMessageJpaEntity, Long> {

    Optional<CenterChatMessageJpaEntity> findByThreadIdAndSenderRoleAndClientMessageId(
            Long threadId, String senderRole, String clientMessageId);

    Optional<CenterChatMessageJpaEntity> findByIdAndThreadId(Long id, Long threadId);

    @Query(
            "select m from CenterChatMessageJpaEntity m where m.threadId = :t and (:cursor is null or m.id < :cursor)"
                    + " order by m.id desc")
    List<CenterChatMessageJpaEntity> pageDesc(@Param("t") Long threadId, @Param("cursor") Long cursor, Pageable page);

    @Query("select m from CenterChatMessageJpaEntity m where m.threadId = :t and m.id > :after order by m.id asc")
    List<CenterChatMessageJpaEntity> after(@Param("t") Long threadId, @Param("after") Long after, Pageable page);

    @Query(
            "select count(m) from CenterChatMessageJpaEntity m where m.threadId = :t and m.senderRole = :role"
                    + " and (:after is null or m.id > :after)")
    long countFrom(@Param("t") Long threadId, @Param("role") String senderRole, @Param("after") Long afterId);
}
