package com.meditationmap.centerchat.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 메시지. images = [{"key":"private/center-chat/…","contentType":…,"sizeBytes":…}] — 키는 어떤 응답에도 내보내지 않는다.
 * 소프트 삭제(DC5): deleted_at·deleted_by 만 채우고 행·원문은 남긴다(신고 증거). 당사자 응답에서는 본문·사진을 감춘다.
 */
@Entity
@Table(
        name = "center_chat_messages",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_ccm_client", columnNames = {"thread_id", "sender_role", "client_message_id"}),
        indexes = {
            @Index(name = "ix_ccm_thread_id", columnList = "thread_id, id"),
            @Index(name = "ix_ccm_thread_sender_id", columnList = "thread_id, sender_role, id")
        })
@Getter
@Setter
@NoArgsConstructor
public class CenterChatMessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "thread_id", nullable = false)
    private Long threadId;

    @Column(name = "sender_role", nullable = false, length = 16)
    private String senderRole;

    @Column(name = "sender_member_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String senderMemberId;

    @Column(name = "body", length = 2000)
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "images", columnDefinition = "json")
    private JsonNode images;

    @Column(name = "client_message_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String clientMessageId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_member_id", length = 36, columnDefinition = "CHAR(36)")
    private String deletedByMemberId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public int imageCount() {
        return images != null && images.isArray() ? images.size() : 0;
    }
}
