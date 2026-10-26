package com.meditationmap.notification.application;

import com.meditationmap.notification.application.port.out.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 온라인/오프라인 분기 (request.md 결정, 계약 2.6 마지막 규칙).
 *
 * <ul>
 *   <li>SSE 연결이 1개 이상이고 하나라도 전송에 성공하면 SSE 로 끝. FCM 은 보내지 않는다
 *   <li>연결이 없거나 모든 전송이 실패하면 등록된 FCM 토큰 전부에 보낸다
 * </ul>
 *
 * 알림은 두 경우 모두 이미 DB 에 저장돼 있다.
 */
@Component
@RequiredArgsConstructor
public class NotificationDelivery {

    private final RealtimeNotifier realtimeNotifier;
    private final PushNotificationService pushNotificationService;

    public Channel deliver(String recipientMemberId, NotificationView notification) {
        if (realtimeNotifier.deliverNotification(recipientMemberId, notification)) {
            return Channel.SSE;
        }
        pushNotificationService.sendToMember(recipientMemberId, notification);
        return Channel.PUSH;
    }

    public enum Channel {
        SSE,
        PUSH
    }
}
