package com.meditationmap.centerchat.infrastructure.jpa;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CenterChatThreadSpringDataRepository extends JpaRepository<CenterChatThreadJpaEntity, Long> {

    Optional<CenterChatThreadJpaEntity> findByOpenKey(String openKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CenterChatThreadJpaEntity t where t.id = :id")
    Optional<CenterChatThreadJpaEntity> findByIdForUpdate(@Param("id") Long id);

    @Query(
            "select t from CenterChatThreadJpaEntity t where t.customerMemberId = :c and t.lastMessageId is not null"
                    + " and (:cursor is null or t.lastMessageId < :cursor) order by t.lastMessageId desc")
    List<CenterChatThreadJpaEntity> customerPage(@Param("c") String customerId, @Param("cursor") Long cursor, Pageable page);

    @Query(
            "select t from CenterChatThreadJpaEntity t where t.placeId in :places and t.lastMessageId is not null"
                    + " and (:cursor is null or t.lastMessageId < :cursor)"
                    + " and (:unanswered = false or (t.status = 'OPEN' and t.lastSenderRole = 'CUSTOMER'))"
                    + " order by t.lastMessageId desc")
    List<CenterChatThreadJpaEntity> ownerPage(
            @Param("places") Collection<String> placeIds,
            @Param("unanswered") boolean unanswered,
            @Param("cursor") Long cursor,
            Pageable page);

    /** 읽음 역행 금지: GREATEST(기존, 요청). @return 바뀐 행 수(값이 실제로 올라갔으면 1) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "UPDATE center_chat_threads SET customer_last_read_message_id = :v WHERE id = :id"
                            + " AND (customer_last_read_message_id IS NULL OR customer_last_read_message_id < :v)",
            nativeQuery = true)
    int raiseCustomerRead(@Param("id") Long id, @Param("v") Long value);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "UPDATE center_chat_threads SET owner_last_read_message_id = :v WHERE id = :id"
                            + " AND (owner_last_read_message_id IS NULL OR owner_last_read_message_id < :v)",
            nativeQuery = true)
    int raiseOwnerRead(@Param("id") Long id, @Param("v") Long value);
}
