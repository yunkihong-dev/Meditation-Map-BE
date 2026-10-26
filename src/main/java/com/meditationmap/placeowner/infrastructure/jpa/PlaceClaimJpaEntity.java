package com.meditationmap.placeowner.infrastructure.jpa;

import com.meditationmap.placeowner.domain.PlaceClaimStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 장소 소유 신청.
 *
 * <p>{@code active_key} 는 PENDING 일 때만 {@code '{placeId}:{memberId}'}, 그 외 NULL 이다. 유니크 키라서 같은 회원의
 * 같은 장소 대기 신청이 둘이 될 수 없다(더블클릭·동시 요청 포함). MySQL 유니크 인덱스는 NULL 을 여러 개 허용하므로 처리된
 * 신청은 얼마든지 남는다. 부분 유니크·생성 컬럼을 쓰지 않는 이유는 Hibernate update 와 운영 SQL 을 같은 모양으로 두기 위해서다.
 */
@Entity
@Table(
        name = "place_claims",
        uniqueConstraints = @UniqueConstraint(name = "uk_place_claims_active_key", columnNames = "active_key"),
        indexes = {
            @Index(name = "ix_place_claims_status_id", columnList = "status, id"),
            @Index(name = "ix_place_claims_applicant", columnList = "applicant_member_id, id"),
            @Index(name = "ix_place_claims_place_status", columnList = "place_id, status")
        })
@Getter
@Setter
@NoArgsConstructor
public class PlaceClaimJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false, length = 255)
    private String placeId;

    @Column(name = "applicant_member_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String applicantMemberId;

    /** VARCHAR 로 둔다 — Hibernate 6 기본은 MySQL ENUM 이라 상태를 늘릴 때마다 ALTER 가 필요해진다. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private PlaceClaimStatus status;

    @Column(name = "active_key", length = 300)
    private String activeKey;

    @Column(name = "contact", nullable = false, length = 100)
    private String contact;

    @Column(name = "memo", length = 2000)
    private String memo;

    @Column(name = "business_registration_number", length = 20)
    private String businessRegistrationNumber;

    /** private/place-claims/... — URL 이 아니다. 어떤 응답에도 내보내지 않는다 */
    @Column(name = "evidence_object_key", length = 512)
    private String evidenceObjectKey;

    @Column(name = "evidence_file_name", length = 255)
    private String evidenceFileName;

    @Column(name = "evidence_content_type", length = 128)
    private String evidenceContentType;

    @Column(name = "evidence_size_bytes")
    private Long evidenceSizeBytes;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "reviewed_by_member_id", length = 36, columnDefinition = "CHAR(36)")
    private String reviewedByMemberId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static String activeKeyOf(String placeId, String memberId) {
        return placeId + ":" + memberId;
    }

    public boolean hasEvidence() {
        return evidenceObjectKey != null;
    }
}
