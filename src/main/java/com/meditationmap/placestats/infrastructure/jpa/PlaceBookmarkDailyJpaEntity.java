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
 * 장소 일별 북마크 증감(KST). 현재 북마크 수는 member_favorite_places 를 세고, 이 표는 "언제 늘고 줄었나" 만 기록한다.
 * 기록은 이 기능 배포 시점부터다 — 그 전 증감 이력은 없다.
 */
@Entity
@Table(name = "place_bookmark_daily")
@IdClass(PlaceStatKey.class)
@Getter
@NoArgsConstructor
public class PlaceBookmarkDailyJpaEntity {

    @Id
    @Column(name = "place_id", length = 255)
    private String placeId;

    @Id
    @Column(name = "stat_date")
    private LocalDate statDate;

    @Column(name = "added_count", nullable = false)
    private int addedCount;

    @Column(name = "removed_count", nullable = false)
    private int removedCount;
}
