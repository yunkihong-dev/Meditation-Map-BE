package com.meditationmap.notification.application;

import java.util.List;

/** 커밋 후 디스패치 트리거. 트랜잭션이 롤백되면 발행되지 않는다(@TransactionalEventListener AFTER_COMMIT). */
public final class NotificationEvents {

    private NotificationEvents() {}

    /** 개별 알림 행들이 저장됨 (신청·승인·반려). */
    public record NotificationsCreated(List<Long> notificationIds) {}

    /** 광고 발송 팬아웃이 저장됨. 행이 많을 수 있어 id 대신 발송 id 로 넘긴다. */
    public record BroadcastCreated(Long broadcastId) {}
}
