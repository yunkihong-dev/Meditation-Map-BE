package com.meditationmap.notification.application;

import com.meditationmap.notification.application.port.out.PushSender;
import com.meditationmap.notification.application.port.out.PushSender.PushMessage;
import com.meditationmap.notification.application.port.out.PushSender.PushResult;
import com.meditationmap.notification.infrastructure.jpa.PushTokenJpaEntity;
import com.meditationmap.notification.infrastructure.jpa.PushTokenSpringDataRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 회원의 등록된 FCM 토큰 전부에 보낸다. 무효 토큰(UNREGISTERED 등)은 지운다(계약 2.7). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushTokenSpringDataRepository tokenRepository;
    private final PushSender pushSender;

    /** @return 하나 이상 전송 성공 여부 */
    public boolean sendToMember(String memberId, NotificationView n) {
        List<PushTokenJpaEntity> tokens = tokenRepository.findByMemberId(memberId);
        if (tokens.isEmpty()) {
            log.debug("[PUSH] member={} 등록된 토큰 없음 — 알림 {} 은 알림함에서만 보인다", memberId, n.id());
            return false;
        }
        boolean chat = "CENTER_CHAT_MESSAGE".equals(n.type()) && "CENTER_CHAT".equals(n.refType());
        PushMessage message =
                new PushMessage(
                        n.id(),
                        n.type(),
                        n.title(),
                        n.body(),
                        n.linkUrl(),
                        chat ? "center-chat-" + n.refId() : null,
                        "CENTER_CHAT".equals(n.refType()) ? n.refId() : null);
        boolean any = false;
        for (PushTokenJpaEntity t : tokens) {
            PushResult result;
            try {
                result = pushSender.send(memberId, t.getToken(), message);
            } catch (RuntimeException e) {
                log.warn("[PUSH] 발송 실패 member={} notification={}", memberId, n.id(), e);
                result = PushResult.FAILED;
            }
            if (result == PushResult.SENT) {
                any = true;
            } else if (result == PushResult.TOKEN_INVALID) {
                tokenRepository.deleteByTokenValue(t.getToken());
                log.info("[PUSH] 무효 토큰 삭제 member={} token={}…", memberId, prefix(t.getToken()));
            }
        }
        return any;
    }

    static String prefix(String token) {
        if (token == null) {
            return "";
        }
        return token.length() <= 8 ? token : token.substring(0, 8);
    }
}
