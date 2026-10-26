package com.meditationmap.placeowner.domain;

/** 소유 신청 상태. PENDING 에서만 승인·반려할 수 있다. */
public enum PlaceClaimStatus {
    PENDING,
    APPROVED,
    REJECTED
}
