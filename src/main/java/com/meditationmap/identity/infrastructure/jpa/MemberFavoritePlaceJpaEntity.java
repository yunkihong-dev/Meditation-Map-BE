package com.meditationmap.identity.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_favorite_places")
@Getter
@NoArgsConstructor
public class MemberFavoritePlaceJpaEntity {

    @EmbeddedId
    private MemberFavoritePlaceId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public MemberFavoritePlaceJpaEntity(String memberId, String placeId) {
        this.id = new MemberFavoritePlaceId(memberId, placeId);
        this.createdAt = Instant.now();
    }
}
