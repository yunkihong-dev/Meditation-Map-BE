package com.meditationmap.placeowner.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/**
 * 승인된 장소 오너. <b>PK = place_id</b> 라서 "장소당 오너 1명" 을 DB 가 보장한다(동시 승인 경쟁의 최종 방어선).
 *
 * <p>{@link Persistable} 을 구현해 새 행은 항상 {@code persist}(INSERT) 로 저장한다. 할당 ID 엔티티를 그냥
 * {@code save} 하면 Spring Data 가 {@code merge} 를 써서, 이미 오너가 있는 장소에서 <b>기존 오너를 조용히 덮어쓴다</b>.
 */
@Entity
@Table(name = "place_owners", indexes = @Index(name = "ix_place_owners_owner", columnList = "owner_member_id"))
@Getter
@Setter
@NoArgsConstructor
public class PlaceOwnerJpaEntity implements Persistable<String> {

    @Id
    @Column(name = "place_id", length = 255)
    private String placeId;

    @Column(name = "owner_member_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String ownerMemberId;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "approved_by_member_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String approvedByMemberId;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    @Transient private boolean isNew = true;

    @Override
    public String getId() {
        return placeId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
