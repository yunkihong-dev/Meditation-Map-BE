package com.meditationmap.placestats.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장소 일별 조회수(KST 날짜). 쓰기는 JDBC upsert(INSERT … ON DUPLICATE KEY UPDATE)로 한다 — 이 엔티티는 스키마 선언용.
 */
@Entity
@Table(name = "place_view_daily")
@IdClass(PlaceStatKey.class)
@Getter
@NoArgsConstructor
public class PlaceViewDailyJpaEntity {

    @Id
    @Column(name = "place_id", length = 255)
    private String placeId;

    @Id
    @Column(name = "stat_date")
    private LocalDate statDate;

    @Column(name = "view_count", nullable = false)
    private int viewCount;
}
