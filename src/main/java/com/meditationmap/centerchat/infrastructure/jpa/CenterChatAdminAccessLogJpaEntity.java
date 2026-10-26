package com.meditationmap.centerchat.infrastructure.jpa;

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

/** 관리자 열람 감사 로그(DC3). 조회 API 없음 — DB 로 감사한다. */
@Entity
@Table(name = "center_chat_admin_access_logs", indexes = @Index(name = "ix_ccaal_thread", columnList = "thread_id, id"))
@Getter
@Setter
@NoArgsConstructor
public class CenterChatAdminAccessLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "thread_id", nullable = false)
    private Long threadId;

    @Column(name = "admin_member_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String adminMemberId;

    @Column(name = "access_kind", nullable = false, length = 16)
    private String accessKind;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;
}
