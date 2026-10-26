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

/** 대화 신고. active_key 는 PENDING 일 때만 '{threadId}:{reporterId}' — 한 사람당 대기 신고 1건(동시 요청 포함). */
@Entity
@Table(
        name = "center_chat_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_ccr_active_key", columnNames = "active_key"),
        indexes = {
            @Index(name = "ix_ccr_status_id", columnList = "status, id"),
            @Index(name = "ix_ccr_thread", columnList = "thread_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class CenterChatReportJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "thread_id", nullable = false)
    private Long threadId;

    @Column(name = "reporter_member_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String reporterMemberId;

    @Column(name = "reporter_role", nullable = false, length = 16)
    private String reporterRole;

    @Column(name = "reason", nullable = false, length = 32)
    private String reason;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "reported_up_to_message_id", nullable = false)
    private Long reportedUpToMessageId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "active_key", length = 80)
    private String activeKey;

    @Column(name = "resolution_action", length = 16)
    private String resolutionAction;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "resolved_by_member_id", length = 36, columnDefinition = "CHAR(36)")
    private String resolvedByMemberId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
