package com.meditationmap.notification.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 알림 1행 = 수신자 1명. 회원·스태프 공용. 광고 팬아웃은 JDBC INSERT…SELECT 로도 들어온다. */
@Entity
@Table(
        name = "notifications",
        indexes = {
            @Index(name = "ix_notifications_recipient_id", columnList = "recipient_member_id, id"),
            @Index(name = "ix_notifications_recipient_unread", columnList = "recipient_member_id, read_at"),
            @Index(name = "ix_notifications_broadcast", columnList = "broadcast_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class NotificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_member_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String recipientMemberId;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @Column(name = "link_url", length = 300)
    private String linkUrl;

    @Column(name = "ref_type", length = 30)
    private String refType;

    @Column(name = "ref_id", length = 64)
    private String refId;

    @Column(name = "broadcast_id")
    private Long broadcastId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
