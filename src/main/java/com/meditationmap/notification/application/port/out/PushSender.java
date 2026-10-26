package com.meditationmap.notification.application.port.out;

/** 오프라인 회원용 푸시 발송 포트 (FCM HTTP v1 / 로깅 폴백). */
public interface PushSender {

    PushResult send(String memberId, String token, PushMessage message);

    /**
     * @param linkUrl FE 내부 경로 또는 null
     */
    record PushMessage(
            String notificationId, String type, String title, String body, String linkUrl, String tag, String threadId) {

        public PushMessage(String notificationId, String type, String title, String body, String linkUrl) {
            this(notificationId, type, title, body, linkUrl, null, null);
        }
    }

    enum PushResult {
        SENT,
        /** 토큰이 만료·해제됨 → 호출자가 토큰을 지운다 */
        TOKEN_INVALID,
        FAILED
    }
}
