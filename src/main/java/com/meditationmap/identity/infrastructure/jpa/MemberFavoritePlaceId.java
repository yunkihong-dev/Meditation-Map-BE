package com.meditationmap.identity.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MemberFavoritePlaceId implements Serializable {

    @Column(name = "member_id", nullable = false, length = 36)
    private String memberId;

    @Column(name = "place_id", nullable = false, length = 64)
    private String placeId;
}
