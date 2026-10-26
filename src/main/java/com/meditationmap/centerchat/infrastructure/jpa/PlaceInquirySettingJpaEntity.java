package com.meditationmap.centerchat.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 장소 문의 방식(C14). places.data(JSON payload)에 두지 않는다 — 관리자 장소 저장이 payload 를 통째로 바꿔 오너 설정을 지울 수
 * 있고, 공개 상세 GET /places/{id} 응답 shape·캐시를 건드리지 않기 위해서다(place_business_infos 와 같은 이유).
 *
 * <p>owner_member_id = 이 설정을 저장한 오너. 지금 오너와 다르면(이관·해제) 설정은 무시되고 기본 CHAT 으로 본다 — 이전 오너의
 * 카카오 링크가 새 오너 센터로 안내되지 않게.
 */
@Entity
@Table(name = "place_inquiry_settings")
@Getter
@Setter
@NoArgsConstructor
public class PlaceInquirySettingJpaEntity {

    @Id
    @Column(name = "place_id", length = 255)
    private String placeId;

    @Column(name = "mode", nullable = false, length = 16)
    private String mode;

    @Column(name = "kakao_type", length = 16)
    private String kakaoType;

    @Column(name = "kakao_url", length = 500)
    private String kakaoUrl;

    @Column(name = "owner_member_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String ownerMemberId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
