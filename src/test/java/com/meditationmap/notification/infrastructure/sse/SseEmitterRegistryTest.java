package com.meditationmap.notification.infrastructure.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.notification.application.NotificationView;
import com.meditationmap.notification.infrastructure.config.NotificationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 인메모리 SSE 레지스트리 (계약 2.6).
 *
 * <p>보장: 연결이 없으면 deliver 가 false(→ FCM 폴백) · 연결이 있으면 true · 회원당 최대 연결 수를 넘으면 가장 오래된 것부터
 * 닫는다 · 이벤트 원문 형식(retry/connected/notification/:hb).
 * <p>보장하지 않음: 실제 HTTP 스트림 flush·타임아웃(런타임 curl 로 실측).
 */
class SseEmitterRegistryTest {

    private static final String MEMBER = "11111111-1111-1111-1111-111111111111";

    private final SseEmitterRegistry registry =
            new SseEmitterRegistry(
                    new NotificationProperties(
                            "http://localhost:3000",
                            new NotificationProperties.Sse(1_800_000L, 25_000L, 5),
                            new NotificationProperties.Push("log"),
                            new NotificationProperties.Fcm(null, null)),
                    new ObjectMapper());

    private static final NotificationView N =
            new NotificationView("1025", "PLACE_CLAIM_REJECTED", "소유 신청이 반려되었어요", "…", "/profile/place-claims",
                    "PLACE_CLAIM", "58", false, null, "2026-10-07T03:20:00.000Z");

    @Test
    @DisplayName("열린 연결이 없으면 false — 호출자가 FCM 으로 넘긴다")
    void 연결_없음() {
        assertThat(registry.deliverNotification(MEMBER, N)).isFalse();
    }

    @Test
    @DisplayName("연결이 있으면 true")
    void 연결_있음() {
        registry.open(MEMBER, 3);
        assertThat(registry.deliverNotification(MEMBER, N)).isTrue();
        assertThat(registry.deliverNotification("someone-else", N)).isFalse();
    }

    @Test
    @DisplayName("회원당 5개를 넘으면 가장 오래된 연결부터 닫는다")
    void 연결_상한() {
        for (int i = 0; i < 6; i++) {
            registry.open(MEMBER, 0);
        }
        assertThat(registry.connectionCount(MEMBER)).isEqualTo(5);
        assertThat(registry.snapshot(MEMBER).stream().mapToLong(c -> c.seq).min().orElseThrow()).isEqualTo(2L);
    }

    @Test
    @DisplayName("이벤트 원문은 계약 2.6 형식이다")
    void 원문_형식() {
        assertThat(RawSseEvent.retry(5000).raw()).isEqualTo("retry: 5000\n\n");
        assertThat(RawSseEvent.event(null, "connected", "{\"unreadCount\":3}").raw())
                .isEqualTo("event: connected\ndata: {\"unreadCount\":3}\n\n");
        assertThat(RawSseEvent.event("1025", "notification", "{}").raw())
                .isEqualTo("id: 1025\nevent: notification\ndata: {}\n\n");
        assertThat(RawSseEvent.heartbeat().raw()).isEqualTo(":hb\n\n");
    }
}
