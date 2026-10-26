package com.meditationmap.notification.application.port.out;

import com.meditationmap.notification.application.NotificationView;

/** 온라인(SSE 연결 있음) 회원에게 즉시 보내는 채널. 구현은 인메모리 SseEmitter 레지스트리. */
public interface RealtimeNotifier {

    /**
     * 회원의 열린 연결 전부에 {@code notification} 이벤트를 보낸다.
     *
     * @return 하나 이상의 연결에 전송 성공하면 true. 연결이 없거나 전부 실패하면 false → 호출자가 FCM 으로 넘긴다
     */
    boolean deliverNotification(String memberId, NotificationView notification);

    /**
     * 이름 있는 이벤트(채팅 {@code chat-*})를 {@code id:} 줄 없이 보낸다. 연결이 없으면 버린다(FCM 으로 넘기지 않는다 — 채팅 C7.1).
     */
    void publishEvent(String memberId, String eventName, Object data);

    /** 회원의 열린 연결 전부에 {@code unread-count} 이벤트를 보낸다. 실패는 무시한다. */
    void publishUnreadCount(String memberId, long unreadCount);
}
