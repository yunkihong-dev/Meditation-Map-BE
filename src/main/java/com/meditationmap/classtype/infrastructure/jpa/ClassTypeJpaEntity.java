package com.meditationmap.classtype.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 전문가가 고르는 클래스 종류. 관리자가 목록을 관리합니다. */
@Entity
@Table(name = "class_types")
@Setter
@Getter
public class ClassTypeJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 60, unique = true)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * 목록에서 내린 항목. 지우지 않고 감추는 이유는, 이미 그 종류를 선택해 둔 전문가의 프로필이
     * 있기 때문입니다. 전문가 화면에서는 사라지지만 기존 값은 그대로 남습니다.
     */
    @Column(nullable = false)
    private boolean active = true;
}
