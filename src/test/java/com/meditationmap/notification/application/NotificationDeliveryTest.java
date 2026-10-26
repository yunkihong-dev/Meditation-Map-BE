package com.meditationmap.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meditationmap.notification.application.port.out.PushSender;
import com.meditationmap.notification.application.port.out.PushSender.PushResult;
import com.meditationmap.notification.application.port.out.RealtimeNotifier;
import com.meditationmap.notification.infrastructure.jpa.PushTokenJpaEntity;
import com.meditationmap.notification.infrastructure.jpa.PushTokenSpringDataRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 온라인/오프라인 분기 (request.md 결정, 계약 2.6 마지막 규칙).
 *
 * <p>보장: SSE 전송 성공 → FCM 안 보냄 · 연결 없음(또는 전부 실패) → 등록 토큰 전부에 FCM · 무효 토큰 삭제.
 * <p>보장하지 않음: AFTER_COMMIT·@Async 배선(런타임 실측), 실제 FCM.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryTest {

    private static final String MEMBER = "11111111-1111-1111-1111-111111111111";
    private static final NotificationView N =
            new NotificationView("1024", "PLACE_CLAIM_APPROVED", "소유 신청이 승인되었어요", "본문",
                    "/profile/places/12/edit", "PLACE_CLAIM", "57", false, null, "2026-10-07T03:12:45.123Z");

    @Mock private RealtimeNotifier realtime;
    @Mock private PushTokenSpringDataRepository tokenRepository;
    @Mock private PushSender pushSender;

    private NotificationDelivery delivery;

    @BeforeEach
    void setUp() {
        delivery = new NotificationDelivery(realtime, new PushNotificationService(tokenRepository, pushSender));
    }

    @Test
    @DisplayName("SSE 연결이 있고 전송에 성공하면 SSE 로 끝, FCM 은 보내지 않는다")
    void 온라인이면_SSE() {
        given(realtime.deliverNotification(MEMBER, N)).willReturn(true);

        assertThat(delivery.deliver(MEMBER, N)).isEqualTo(NotificationDelivery.Channel.SSE);
        verify(pushSender, never()).send(anyString(), anyString(), any());
        verify(tokenRepository, never()).findByMemberId(anyString());
    }

    @Test
    @DisplayName("SSE 연결이 없거나 전부 실패하면 등록된 토큰 전부에 FCM 으로 보낸다")
    void 오프라인이면_FCM() {
        given(realtime.deliverNotification(MEMBER, N)).willReturn(false);
        given(tokenRepository.findByMemberId(MEMBER)).willReturn(List.of(token("tok-a"), token("tok-b")));
        given(pushSender.send(eq(MEMBER), anyString(), any())).willReturn(PushResult.SENT);

        assertThat(delivery.deliver(MEMBER, N)).isEqualTo(NotificationDelivery.Channel.PUSH);
        verify(pushSender).send(eq(MEMBER), eq("tok-a"), eq(new PushSender.PushMessage(
                "1024", "PLACE_CLAIM_APPROVED", "소유 신청이 승인되었어요", "본문", "/profile/places/12/edit")));
        verify(pushSender).send(eq(MEMBER), eq("tok-b"), any());
    }

    @Test
    @DisplayName("FCM 이 토큰 무효를 돌려주면 그 토큰을 지운다")
    void 무효_토큰_삭제() {
        given(realtime.deliverNotification(MEMBER, N)).willReturn(false);
        given(tokenRepository.findByMemberId(MEMBER)).willReturn(List.of(token("dead")));
        given(pushSender.send(MEMBER, "dead", new PushSender.PushMessage(
                "1024", "PLACE_CLAIM_APPROVED", "소유 신청이 승인되었어요", "본문", "/profile/places/12/edit")))
                .willReturn(PushResult.TOKEN_INVALID);

        delivery.deliver(MEMBER, N);

        verify(tokenRepository).deleteByTokenValue("dead");
    }

    private static PushTokenJpaEntity token(String value) {
        PushTokenJpaEntity t = new PushTokenJpaEntity();
        t.setToken(value);
        t.setMemberId(MEMBER);
        return t;
    }
}
