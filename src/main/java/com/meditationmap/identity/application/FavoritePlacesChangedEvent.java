package com.meditationmap.identity.application;

import java.util.Set;

/**
 * 회원 북마크(즐겨찾기) 변경. 같은 트랜잭션 안에서 발행된다 — 통계 컨텍스트가 일별 증감을 기록한다.
 *
 * @param added 새로 북마크한 장소 ID
 * @param removed 북마크를 해제한 장소 ID
 */
public record FavoritePlacesChangedEvent(String memberId, Set<String> added, Set<String> removed) {}
