package com.meditationmap.notification.infrastructure.push;

import com.meditationmap.notification.application.port.out.PushSender;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code app.notification.push.provider} 가 fcm 이 아닐 때(기본 log). 실제 푸시 대신 한 줄만 남긴다(계약 2.7).
 * 토큰은 앞 8자만 찍는다.
 */
@Slf4j
public class LoggingPushSender implements PushSender {

    @Override
    public PushResult send(String memberId, String token, PushMessage message) {
        String prefix = token == null ? "" : token.substring(0, Math.min(8, token.length()));
        log.info(
                "[PUSH:LOG] to={} token={}… title={} notificationId={} linkUrl={}",
                memberId,
                prefix,
                message.title(),
                message.notificationId(),
                message.linkUrl());
        return PushResult.SENT;
    }
}
