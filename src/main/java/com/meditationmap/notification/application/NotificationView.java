package com.meditationmap.notification.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.meditationmap.notification.infrastructure.jpa.NotificationJpaEntity;
import com.meditationmap.shared.domain.ApiTimestamps;

/**
 * 계약 1절 {@code AppNotification}. REST 목록 원소와 SSE {@code notification} 이벤트 data 가 같은 객체다.
 *
 * <p>null 인 값도 키는 항상 내보낸다(계약 0절).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"id", "type", "title", "body", "linkUrl", "refType", "refId", "read", "readAt", "createdAt"})
public record NotificationView(
        String id,
        String type,
        String title,
        String body,
        String linkUrl,
        String refType,
        String refId,
        boolean read,
        String readAt,
        String createdAt) {

    public static NotificationView from(NotificationJpaEntity e) {
        return new NotificationView(
                String.valueOf(e.getId()),
                e.getType(),
                e.getTitle(),
                e.getBody(),
                e.getLinkUrl(),
                e.getRefType(),
                e.getRefId(),
                e.getReadAt() != null,
                ApiTimestamps.format(e.getReadAt()),
                ApiTimestamps.format(e.getCreatedAt()));
    }
}
