package com.meditationmap.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.notification.application.BroadcastService.BroadcastCommand;
import com.meditationmap.notification.application.BroadcastService.Target;
import com.meditationmap.notification.infrastructure.jdbc.NotificationFanoutJdbcRepository;
import com.meditationmap.notification.infrastructure.jpa.NotificationBroadcastJpaEntity;
import com.meditationmap.notification.infrastructure.jpa.NotificationBroadcastSpringDataRepository;
import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 광고 발송 규칙 (계약 3.1, D3).
 *
 * <p>보장: KST 21~08시 거부 · "(광고) " 접두(중복 없음) · requestId 중복 409(사전 조회·유니크 충돌 모두) · 수신자 0명 400 ·
 * 외부 링크 400.
 * <p>보장하지 않음: INSERT…SELECT 의 동의자 필터 SQL(런타임 실측), 트랜잭션 롤백.
 */
@ExtendWith(MockitoExtension.class)
class BroadcastServiceTest {

    private static final String ADMIN = "f277b1a5-8fa1-4b51-a5bb-54fdd8f91f68";
    private static final String REQ = "6f1c2b0e-3a4d-4f7e-9b1a-2c3d4e5f6a7b";

    @Mock private NotificationBroadcastSpringDataRepository broadcastRepository;
    @Mock private NotificationFanoutJdbcRepository fanoutRepository;
    @Mock private MemberDirectory memberDirectory;
    @Mock private ApplicationEventPublisher events;

    private BroadcastService at(String isoUtc) {
        return new BroadcastService(broadcastRepository, fanoutRepository, memberDirectory, events,
                Clock.fixed(Instant.parse(isoUtc), ZoneOffset.UTC));
    }

    private static BroadcastCommand all(String linkUrl) {
        return new BroadcastCommand(REQ, "가을 명상 주간", "본문", linkUrl, Target.ALL, List.of());
    }

    @Test
    @DisplayName("KST 21:00~07:59 는 400 BROADCAST_QUIET_HOURS, 08:00·20:59 는 통과")
    void 야간_차단() {
        assertThat(BroadcastService.isQuietHours(Instant.parse("2026-10-07T12:00:00Z"))).isTrue(); // 21:00 KST
        assertThat(BroadcastService.isQuietHours(Instant.parse("2026-10-07T22:59:59Z"))).isTrue(); // 07:59 KST
        assertThat(BroadcastService.isQuietHours(Instant.parse("2026-10-07T23:00:00Z"))).isFalse(); // 08:00 KST
        assertThat(BroadcastService.isQuietHours(Instant.parse("2026-10-07T11:59:59Z"))).isFalse(); // 20:59 KST

        assertCode(() -> at("2026-10-07T13:00:00Z").send(ADMIN, all(null)), ErrorCode.BROADCAST_QUIET_HOURS);
        verify(fanoutRepository, never()).insertForAllConsented(any());
    }

    @Test
    @DisplayName("제목에 (광고) 접두를 붙이되 이미 있으면 중복으로 붙이지 않는다")
    void 광고_접두() {
        assertThat(BroadcastService.withAdPrefix("가을 명상 주간")).isEqualTo("(광고) 가을 명상 주간");
        assertThat(BroadcastService.withAdPrefix("(광고) 가을")).isEqualTo("(광고) 가을");
    }

    @Test
    @DisplayName("requestId 중복은 409 BROADCAST_DUPLICATE — 사전 조회와 유니크 충돌 둘 다")
    void 중복_요청() {
        BroadcastService svc = at("2026-10-07T03:00:00Z");
        given(broadcastRepository.existsByRequestId(REQ)).willReturn(true);
        assertCode(() -> svc.send(ADMIN, all(null)), ErrorCode.BROADCAST_DUPLICATE);

        given(broadcastRepository.existsByRequestId(REQ)).willReturn(false);
        given(broadcastRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("dup"));
        assertCode(() -> svc.send(ADMIN, all(null)), ErrorCode.BROADCAST_DUPLICATE);
    }

    @Test
    @DisplayName("동의한 수신자가 0명이면 400 BROADCAST_NO_RECIPIENTS 이고 디스패치 이벤트를 내지 않는다")
    void 수신자_없음() {
        BroadcastService svc = at("2026-10-07T03:00:00Z");
        given(broadcastRepository.saveAndFlush(any())).willAnswer(inv -> {
            NotificationBroadcastJpaEntity b = inv.getArgument(0);
            b.setId(7L);
            return b;
        });
        given(fanoutRepository.insertForAllConsented(any())).willReturn(0);

        assertCode(() -> svc.send(ADMIN, all("/meditation/map")), ErrorCode.BROADCAST_NO_RECIPIENTS);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("정상 발송은 (광고) 접두 제목으로 팬아웃하고 수신자 수·unknownEmails 를 돌려준다")
    void 정상_발송() {
        BroadcastService svc = at("2026-10-07T03:00:00Z");
        given(broadcastRepository.saveAndFlush(any())).willAnswer(inv -> {
            NotificationBroadcastJpaEntity b = inv.getArgument(0);
            b.setId(7L);
            return b;
        });
        given(memberDirectory.resolveNonStaffByEmails(List.of("a@example.com", "x@example.com")))
                .willReturn(new MemberDirectory.EmailResolution(List.of("m-a"), List.of("x@example.com")));
        given(fanoutRepository.insertForConsentedMembers(any(), any())).willReturn(1);

        BroadcastService.BroadcastResult r =
                svc.send(ADMIN, new BroadcastCommand(REQ, "안내", "본문", null, Target.EMAILS,
                        List.of("a@example.com", "x@example.com")));

        assertThat(r.broadcastId()).isEqualTo("7");
        assertThat(r.recipientCount()).isEqualTo(1);
        assertThat(r.unknownEmails()).containsExactly("x@example.com");
        verify(events).publishEvent(new NotificationEvents.BroadcastCreated(7L));
    }

    @Test
    @DisplayName("외부 링크·// 링크는 400 INVALID_LINK_URL")
    void 외부_링크() {
        BroadcastService svc = at("2026-10-07T03:00:00Z");
        assertCode(() -> svc.send(ADMIN, all("https://evil.example")), ErrorCode.INVALID_LINK_URL);
        assertCode(() -> svc.send(ADMIN, all("//evil.example")), ErrorCode.INVALID_LINK_URL);
        assertCode(() -> svc.send(ADMIN, all("/redirect?to=http://evil")), ErrorCode.INVALID_LINK_URL);
    }

    private static void assertCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
