package com.meditationmap.interest.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 관심사(주제). 사용자는 온보딩에서 카드로 고르고, 전문가는 가르치는 주제로 고릅니다.
 *
 * <p>이름·설명·사진을 한 곳에서 관리해 온보딩 카드와 칩 목록이 어긋나지 않게 합니다.
 */
@Entity
@Table(name = "interests")
@Setter
@Getter
public class InterestJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 60, unique = true)
    private String name;

    /** 카드를 눌렀을 때 제목과 함께 보이는 설명 */
    @Column(length = 300)
    private String description;

    /** 업로드한 이미지의 공개 URL. 없으면 프런트가 색만 있는 카드로 그립니다. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * 목록에서 내린 항목. 지우지 않고 감추는 이유는, 이미 그 주제를 골라 둔 사용자·전문가가
     * 있기 때문입니다. 새로 고르는 것만 막고 저장된 값은 그대로 둡니다.
     */
    @Column(nullable = false)
    private boolean active = true;
}
