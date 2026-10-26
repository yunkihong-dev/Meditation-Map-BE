package com.meditationmap.notification.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 광고 발송 이력. request_id 유니크로 더블클릭·재시도 중복을 막는다. */
@Entity
@Table(
        name = "notification_broadcasts",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_notification_broadcasts_request", columnNames = "request_id"))
@Getter
@Setter
@NoArgsConstructor
public class NotificationBroadcastJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String requestId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @Column(name = "link_url", length = 300)
    private String linkUrl;

    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "sent_by_member_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String sentByMemberId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
