package com.meditationmap.centerchat.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meditationmap.centerchat.application.ChatAccess.Viewer;
import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatReportJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatReportSpringDataRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadSpringDataRepository;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

/** 신고 중복(active_key 유니크 충돌 포함)은 409 — 500 으로 새지 않는다(C9 계약 끝). 이미 처리된 신고 409. */
@ExtendWith(MockitoExtension.class)
class ChatReportServiceTest {

    private static final Viewer CUSTOMER = new Viewer("cust", SenderRole.CUSTOMER);

    @Mock private ChatAccess access;
    @Mock private CenterChatReportSpringDataRepository reports;
    @Mock private CenterChatThreadSpringDataRepository threads;
    @Mock private ChatPlaceLookup places;
    @Mock private PlaceOwnership ownership;
    @Mock private MemberDirectory members;
    @Mock private ChatNotifier notifier;
    @Mock private ApplicationEventPublisher events;

    private ChatReportService service;
    private final CenterChatThreadJpaEntity t = new CenterChatThreadJpaEntity();

    @BeforeEach
    void setUp() {
        service = new ChatReportService(access, reports, threads, places, ownership, members, notifier, events,
                Clock.fixed(Instant.parse("2026-10-08T02:00:00Z"), ZoneOffset.UTC));
        t.setId(31L);
        t.setPlaceId("12");
        t.setCustomerMemberId("cust");
        t.setStatus("OPEN");
        t.setLastMessageId(905L);
    }

    @Test
    @DisplayName("내 PENDING 신고가 이미 있으면 409 CENTER_CHAT_REPORT_ALREADY_PENDING, 저장·알림 없음")
    void 사전조회_중복() {
        given(access.lock(CUSTOMER, "31")).willReturn(t);
        given(reports.findByActiveKey("31:cust")).willReturn(Optional.of(new CenterChatReportJpaEntity()));

        assertCode(() -> service.create(CUSTOMER, "31", "ABUSE", "욕설"), ErrorCode.CENTER_CHAT_REPORT_ALREADY_PENDING);
        verify(reports, never()).saveAndFlush(any());
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("동시 신고로 active_key 유니크 충돌(DataIntegrityViolation)이면 409 로 바꾼다 — 500 아님, 알림 없음")
    void 유니크_충돌() {
        given(access.lock(CUSTOMER, "31")).willReturn(t);
        given(reports.findByActiveKey("31:cust")).willReturn(Optional.empty());
        given(reports.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("Duplicate entry for key uk_ccr_active_key"));

        assertCode(() -> service.create(CUSTOMER, "31", "SPAM", null), ErrorCode.CENTER_CHAT_REPORT_ALREADY_PENDING);
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("이미 처리된 신고를 다시 처리하면 409 CENTER_CHAT_REPORT_ALREADY_RESOLVED, 대화 상태 그대로")
    void 이미_처리() {
        CenterChatReportJpaEntity r = new CenterChatReportJpaEntity();
        r.setId(7L);
        r.setThreadId(31L);
        r.setStatus("DISMISSED");
        given(reports.findThreadIdById(7L)).willReturn(Optional.of(31L));
        given(threads.findByIdForUpdate(31L)).willReturn(Optional.of(t));
        given(reports.findByIdForUpdate(7L)).willReturn(Optional.of(r));

        assertCode(() -> service.resolve("admin", "7", "BLOCK_THREAD", null), ErrorCode.CENTER_CHAT_REPORT_ALREADY_RESOLVED);
        verify(threads, never()).save(any());
        verifyNoInteractions(events, notifier);
    }

    private static void assertCode(Runnable r, ErrorCode c) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode()).isEqualTo(c);
    }
}
