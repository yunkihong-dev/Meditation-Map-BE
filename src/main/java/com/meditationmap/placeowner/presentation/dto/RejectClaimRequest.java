package com.meditationmap.placeowner.presentation.dto;

/** POST /admin/place-claims/{id}/reject 본문. 길이 검증은 서비스가 한다(1~500자). */
public record RejectClaimRequest(String reason) {}
