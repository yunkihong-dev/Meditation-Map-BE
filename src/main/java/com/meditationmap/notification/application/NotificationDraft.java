package com.meditationmap.notification.application;

import com.meditationmap.notification.domain.NotificationRefType;
import com.meditationmap.notification.domain.NotificationType;

/** 다른 컨텍스트가 알림을 만들 때 넘기는 내용. 수신자는 따로 받는다. */
public record NotificationDraft(
        NotificationType type,
        String title,
        String body,
        String linkUrl,
        NotificationRefType refType,
        String refId) {}
