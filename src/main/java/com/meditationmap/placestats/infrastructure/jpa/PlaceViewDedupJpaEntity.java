package com.meditationmap.placestats.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조회 중복 억제 — (장소, KST 날짜, 조회자 해시) 가 처음일 때만 일별 조회수를 올린다. 조회자 해시는 회원 ID 또는 IP+UA 의
 * SHA-256(서버 비밀 + 날짜 솔트)이라 원문 IP 를 저장하지 않는다. 이틀 지난 행은 매일 지운다.
 */
@Entity
@Table(name = "place_view_dedup")
@IdClass(PlaceViewDedupJpaEntity.Key.class)
@Getter
@NoArgsConstructor
public class PlaceViewDedupJpaEntity {

    @Id
    @Column(name = "place_id", length = 255)
    private String placeId;

    @Id
    @Column(name = "stat_date")
    private LocalDate statDate;

    @Id
    @Column(name = "viewer_hash", length = 64, columnDefinition = "CHAR(64)")
    private String viewerHash;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private String placeId;
        private LocalDate statDate;
        private String viewerHash;
    }
}
