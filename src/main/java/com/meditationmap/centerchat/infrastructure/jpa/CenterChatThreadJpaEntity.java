package com.meditationmap.centerchat.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 대화 = 고객 1 × 장소 1 × 프로그램 0..1. 오너는 저장하지 않는다 — 요청 시점의 place_owners 로 판정(DC2: 오너가 바뀌면 새 오너가
 * 이어받고 이전 오너는 즉시 차단). open_key 유니크로 같은 키의 대화가 둘 생기지 않는다(동시 시작 포함).
 */
@Entity
@Table(
        name = "center_chat_threads",
        uniqueConstraints = @UniqueConstraint(name = "uk_center_chat_threads_open_key", columnNames = "open_key"),
        indexes = {
            @Index(name = "ix_cct_customer_last", columnList = "customer_member_id, last_message_id"),
            @Index(name = "ix_cct_place_last", columnList = "place_id, last_message_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class CenterChatThreadJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false, length = 255)
    private String placeId;

    @Column(name = "customer_member_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String customerMemberId;

    @Column(name = "program_id", length = 100)
    private String programId;

    @Column(name = "program_title", length = 200)
    private String programTitle;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "open_key", length = 400)
    private String openKey;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_sender_role", length = 16)
    private String lastSenderRole;

    @Column(name = "customer_last_read_message_id")
    private Long customerLastReadMessageId;

    @Column(name = "owner_last_read_message_id")
    private Long ownerLastReadMessageId;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
