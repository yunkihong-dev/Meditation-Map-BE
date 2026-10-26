package com.meditationmap.placeowner.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 장소의 비공개 사업자 정보. places.data(JSON payload) 에 넣지 않는 이유: 공개 상세 GET /places/{id} 가 payload 를 통째로
 * 내보내므로 거기 두면 누구나 읽는다. 오너·관리자 응답에서만 보인다.
 */
@Entity
@Table(name = "place_business_infos")
@Getter
@Setter
@NoArgsConstructor
public class PlaceBusinessInfoJpaEntity {

    @Id
    @Column(name = "place_id", length = 255)
    private String placeId;

    @Column(name = "business_registration_number", length = 20)
    private String businessRegistrationNumber;

    @Column(name = "updated_by_member_id", length = 36, columnDefinition = "CHAR(36)")
    private String updatedByMemberId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
