package com.meditationmap.notification.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 광고성 알림 수신 동의 (D3). 동의한 회원에게만 광고 알림(인앱·푸시)을 보낸다.
 *
 * <p>행이 없으면 미동의다. 철회해도 행을 지우지 않고 {@code revoked_at} 을 남긴다(동의 이력 증빙).
 */
@Entity
@Table(name = "member_marketing_consents")
@Getter
@Setter
@NoArgsConstructor
public class MarketingConsentJpaEntity {

    @Id
    @Column(name = "member_id", length = 36, columnDefinition = "CHAR(36)")
    private String memberId;

    @Column(name = "push_agreed", nullable = false)
    private boolean pushAgreed;

    @Column(name = "agreed_at")
    private Instant agreedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
