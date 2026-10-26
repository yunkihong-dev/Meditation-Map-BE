package com.meditationmap.notification.infrastructure.push;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;
import com.meditationmap.notification.application.port.out.PushSender;
import lombok.extern.slf4j.Slf4j;

/**
 * FCM HTTP v1 발송(Firebase Admin SDK). 메시지 모양은 계약 2.7.
 *
 * <ul>
 *   <li>{@code data} 값은 모두 문자열. linkUrl 이 null 이면 키를 뺀다
 *   <li>{@code webpush.fcm_options.link} 는 웹 base + linkUrl, linkUrl 이 없으면 웹 base + {@code /notifications}
 *   <li>UNREGISTERED·INVALID_ARGUMENT 는 토큰 무효로 보고 호출자가 토큰을 지운다
 * </ul>
 */
@Slf4j
public class FcmPushSender implements PushSender {

    private final FirebaseMessaging messaging;
    private final String webBaseUrl;

    public FcmPushSender(FirebaseApp app, String webBaseUrl) {
        this.messaging = FirebaseMessaging.getInstance(app);
        this.webBaseUrl = webBaseUrl == null ? "" : webBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public PushResult send(String memberId, String token, PushMessage m) {
        Message.Builder b =
                Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder().setTitle(m.title()).setBody(m.body()).build())
                        .putData("notificationId", m.notificationId())
                        .putData("type", m.type())
                        .setWebpushConfig(webpush(m));
        if (m.linkUrl() != null) {
            b.putData("linkUrl", m.linkUrl());
        }
        if (m.threadId() != null) {
            b.putData("threadId", m.threadId());
        }
        try {
            messaging.send(b.build());
            return PushResult.SENT;
        } catch (FirebaseMessagingException e) {
            PushResult result = classify(e.getMessagingErrorCode(), e.getMessage());
            if (result == PushResult.FAILED) {
                log.warn(
                        "[PUSH:FCM] 발송 실패(토큰 유지) member={} code={} errorCode={} msg={}",
                        memberId,
                        e.getMessagingErrorCode(),
                        e.getErrorCode(),
                        e.getMessage());
            }
            return result;
        }
    }

    /**
     * 토큰을 지워도 되는 오류인지 가른다(계약 2.7 — "UNREGISTERED 또는 토큰 관련 INVALID_ARGUMENT").
     *
     * <ul>
     *   <li>UNREGISTERED(404): 앱 삭제·토큰 만료 → 삭제
     *   <li>INVALID_ARGUMENT(400) 중 <b>등록 토큰이 잘못된 경우만</b> 삭제. FCM 은 이때 "The registration token is not a
     *       valid FCM registration token" 류 메시지를 준다. 그 밖의 INVALID_ARGUMENT(링크가 https 가 아님, payload 형식
     *       등)는 서버 쪽 메시지 문제라 토큰은 멀쩡하다 → 지우면 그 회원은 영영 푸시를 못 받는다. 유지하고 WARN
     *   <li>그 외(QUOTA·UNAVAILABLE·INTERNAL·SENDER_ID_MISMATCH 등) → 유지
     * </ul>
     */
    static PushResult classify(MessagingErrorCode code, String message) {
        if (code == MessagingErrorCode.UNREGISTERED) {
            return PushResult.TOKEN_INVALID;
        }
        if (code == MessagingErrorCode.INVALID_ARGUMENT && isRegistrationTokenProblem(message)) {
            return PushResult.TOKEN_INVALID;
        }
        return PushResult.FAILED;
    }

    private static boolean isRegistrationTokenProblem(String message) {
        if (message == null) {
            return false;
        }
        String m = message.toLowerCase(java.util.Locale.ROOT);
        return m.contains("registration token") || m.contains("registration-token");
    }

    /** 채팅 알림은 tag 로 기기 알림 센터에서 같은 대화 알림을 하나로 교체한다(채팅 C7.2). */
    private WebpushConfig webpush(PushMessage m) {
        WebpushConfig.Builder w =
                WebpushConfig.builder().setFcmOptions(WebpushFcmOptions.withLink(linkFor(m.linkUrl())));
        if (m.tag() != null) {
            w.setNotification(
                    com.google.firebase.messaging.WebpushNotification.builder()
                            .setTitle(m.title())
                            .setBody(m.body())
                            .setTag(m.tag())
                            .setRenotify(true)
                            .build());
        }
        return w.build();
    }

    String linkFor(String linkUrl) {
        return webBaseUrl + (linkUrl == null ? "/notifications" : linkUrl);
    }
}
