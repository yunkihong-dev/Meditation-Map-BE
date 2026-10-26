package com.meditationmap.notification.infrastructure.jpa;

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

/** FCM 웹 푸시 토큰. 토큰 하나는 마지막에 등록한 회원 한 명에게만 속한다(유니크). */
@Entity
@Table(
        name = "push_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_tokens_token", columnNames = "token"),
        indexes = @Index(name = "ix_push_tokens_member", columnList = "member_id"))
@Getter
@Setter
@NoArgsConstructor
public class PushTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, length = 512)
    private String token;

    @Column(name = "member_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String memberId;

    @Column(name = "platform", nullable = false, length = 16)
    private String platform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
