package com.meditationmap.placestats.application;

/**
 * 조회 비콘 요청에서 뽑은 조회자 정보. 원문 IP·UA 는 해시에만 쓰고 저장하지 않는다.
 *
 * @param memberId 로그인 회원 UUID, 비로그인이면 null
 * @param clientIp CloudFront 가 붙인 X-Forwarded-For 마지막 값(없으면 remoteAddr)
 * @param staffSession 관리자 쿠키가 실려 왔는지 — 스태프 조회는 세지 않는다
 */
public record ViewerContext(String memberId, String clientIp, String userAgent, boolean staffSession) {}
