package com.meditationmap.notification.infrastructure.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSpringDataRepository extends JpaRepository<NotificationJpaEntity, Long> {

    List<NotificationJpaEntity> findByRecipientMemberIdOrderByIdDesc(String recipientMemberId, Pageable page);

    List<NotificationJpaEntity> findByRecipientMemberIdAndIdLessThanOrderByIdDesc(
            String recipientMemberId, Long cursor, Pageable page);

    long countByRecipientMemberIdAndReadAtIsNull(String recipientMemberId);

    Optional<NotificationJpaEntity> findByIdAndRecipientMemberId(Long id, String recipientMemberId);

    @Modifying
    @Query(
            "update NotificationJpaEntity n set n.readAt = :now"
                    + " where n.recipientMemberId = :recipient and n.readAt is null")
    int markAllRead(@Param("recipient") String recipientMemberId, @Param("now") Instant now);

    /** 채팅 알림 합치기(채팅 C7.2): 같은 대화의 미확인 채팅 알림을 지운다. */
    @Modifying
    @Query(
            "delete from NotificationJpaEntity n where n.recipientMemberId = :r and n.readAt is null"
                    + " and n.type = :type and n.refType = :refType and n.refId = :refId")
    int deleteUnreadByRef(
            @Param("r") String recipient,
            @Param("type") String type,
            @Param("refType") String refType,
            @Param("refId") String refId);

    /** 대화 읽음 → 그 대화의 미확인 채팅 알림 읽음(채팅 C3.7). */
    @Modifying
    @Query(
            "update NotificationJpaEntity n set n.readAt = :now where n.recipientMemberId = :r and n.readAt is null"
                    + " and n.type = :type and n.refType = :refType and n.refId = :refId")
    int markReadByRef(
            @Param("r") String recipient,
            @Param("type") String type,
            @Param("refType") String refType,
            @Param("refId") String refId,
            @Param("now") Instant now);

    /** 광고 팬아웃 디스패치용 — id 순으로 잘라 읽는다. */
    List<NotificationJpaEntity> findByBroadcastIdAndIdGreaterThanOrderByIdAsc(
            Long broadcastId, Long afterId, Pageable page);
}
