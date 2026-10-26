package com.meditationmap.notification.domain;

/** 알림 종류 (계약 1절). FE 는 모르는 값이 와도 일반 알림으로 렌더한다. */
public enum NotificationType {
    AD,
    PLACE_CLAIM_SUBMITTED,
    PLACE_CLAIM_APPROVED,
    PLACE_CLAIM_REJECTED,
    CENTER_CHAT_MESSAGE,
    CENTER_CHAT_REPORTED,
    CENTER_CHAT_REPORT_RESOLVED
}
