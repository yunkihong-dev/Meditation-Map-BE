package com.meditationmap.notification.presentation;

import com.meditationmap.notification.application.CursorPage;
import com.meditationmap.notification.application.CursorRequest;
import com.meditationmap.notification.application.NotificationQueryService;
import com.meditationmap.notification.application.NotificationReadService;
import com.meditationmap.notification.application.NotificationView;
import com.meditationmap.notification.application.PushTokenService;
import com.meditationmap.notification.infrastructure.sse.SseEmitterRegistry;
import com.meditationmap.notification.presentation.dto.PushTokenRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 회원(/me)·관리자(/admin) 알림 컨트롤러가 공유하는 동작. 응답 shape 이 완전히 같아야 하므로(계약 3절) 한곳에 둔다.
 * 차이는 수신자 ID 를 어느 쿠키의 principal 에서 얻느냐뿐이다.
 */
@Component
@RequiredArgsConstructor
class NotificationEndpoints {

    static final MediaType EVENT_STREAM_UTF8 = new MediaType(MediaType.TEXT_EVENT_STREAM, StandardCharsets.UTF_8);

    private final NotificationQueryService queryService;
    private final NotificationReadService readService;
    private final PushTokenService pushTokenService;
    private final SseEmitterRegistry sseRegistry;

    CursorPage<NotificationView> list(String memberId, String cursor, Integer size) {
        return queryService.page(memberId, CursorRequest.parse(cursor, size));
    }

    Map<String, Long> unreadCount(String memberId) {
        return Map.of("unreadCount", queryService.unreadCount(memberId));
    }

    NotificationReadService.ReadResult markRead(String memberId, String id) {
        return readService.markRead(memberId, id);
    }

    NotificationReadService.ReadAllResult markAllRead(String memberId) {
        return readService.markAllRead(memberId);
    }

    ResponseEntity<SseEmitter> stream(String memberId) {
        SseEmitter emitter = sseRegistry.open(memberId, queryService.unreadCount(memberId));
        return ResponseEntity.ok()
                .contentType(EVENT_STREAM_UTF8)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    ResponseEntity<Void> registerToken(String memberId, PushTokenRequest body) {
        pushTokenService.register(memberId, body.token().trim(), body.platform());
        return ResponseEntity.noContent().build();
    }

    ResponseEntity<Void> unregisterToken(String memberId, PushTokenRequest body) {
        pushTokenService.unregister(memberId, body.token().trim());
        return ResponseEntity.noContent().build();
    }
}
