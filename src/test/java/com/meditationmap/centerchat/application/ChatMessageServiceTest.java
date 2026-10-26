package com.meditationmap.centerchat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.centerchat.application.ChatAccess.Viewer;
import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.centerchat.infrastructure.jdbc.CenterChatJdbcRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatMessageJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatMessageSpringDataRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadSpringDataRepository;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.notification.application.NotificationReadService;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.storage.application.port.out.ObjectStoragePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 전송 멱등(clientMessageId)·차단·읽음 역행 방지·소프트 삭제(C3.6·C3.7·C9). DB 없이 — 행 잠금·GREATEST SQL 은 런타임 실측.
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    private static final String CMID = "6f1c2b0e-3a4d-4f7e-9b1a-2c3d4e5f6a7b";
    private static final Viewer CUSTOMER = new Viewer("cust", SenderRole.CUSTOMER);

    @Mock private ChatAccess access;
    @Mock private CenterChatThreadSpringDataRepository threads;
    @Mock private CenterChatMessageSpringDataRepository messages;
    @Mock private ChatPlaceLookup places;
    @Mock private PlaceOwnership ownership;
    @Mock private MemberDirectory members;
    @Mock private ChatNotifier notifier;
    @Mock private NotificationReadService notificationReads;
    @Mock private ObjectProvider<ObjectStoragePort> storage;
    @Mock private ApplicationEventPublisher events;
    @Mock private CenterChatJdbcRepository jdbc;

    private ChatMessageService service;
    private final CenterChatThreadJpaEntity t = new CenterChatThreadJpaEntity();

    @BeforeEach
    void setUp() {
        ChatAssembler assembler = new ChatAssembler(places, members, messages, jdbc);
        service = new ChatMessageService(access, threads, messages, places, ownership, members, assembler, notifier,
                notificationReads, storage, events, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-08T01:00:00Z"), ZoneOffset.UTC));
        t.setId(31L);
        t.setPlaceId("12");
        t.setCustomerMemberId("cust");
        t.setStatus("OPEN");
    }

    private static CenterChatMessageJpaEntity msg(long id, String role, String member) {
        CenterChatMessageJpaEntity m = new CenterChatMessageJpaEntity();
        m.setId(id);
        m.setThreadId(31L);
        m.setSenderRole(role);
        m.setSenderMemberId(member);
        m.setBody("안녕하세요");
        m.setClientMessageId(CMID);
        m.setCreatedAt(Instant.parse("2026-10-08T00:59:00Z"));
        return m;
    }

    @Test
    @DisplayName("같은 clientMessageId 재전송은 새로 만들지 않고 200(created=false) + 기존 메시지, 알림·SSE 없음")
    void 멱등_재전송() {
        given(access.lock(CUSTOMER, "31")).willReturn(t);
        given(messages.findByThreadIdAndSenderRoleAndClientMessageId(31L, "CUSTOMER", CMID))
                .willReturn(Optional.of(msg(905, "CUSTOMER", "cust")));

        ChatMessageService.SendResult r = service.send(CUSTOMER, "31", CMID.toUpperCase(), "다른 내용", List.of());

        assertThat(r.created()).isFalse();
        assertThat(r.message().id()).isEqualTo("905");
        assertThat(r.message().body()).isEqualTo("안녕하세요");
        verify(messages, never()).saveAndFlush(any());
        verifyNoInteractions(notifier, events);
    }

    @Test
    @DisplayName("새 전송: 201(created) — 메시지 저장, 대화 last_* 갱신, 오너에게 합친 알림(n=받는 쪽 미확인 수), 커밋 뒤 SSE 이벤트")
    void 새_전송() {
        t.setOwnerLastReadMessageId(900L);
        given(access.lock(CUSTOMER, "31")).willReturn(t);
        given(messages.findByThreadIdAndSenderRoleAndClientMessageId(31L, "CUSTOMER", CMID)).willReturn(Optional.empty());
        com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity place =
                org.mockito.Mockito.mock(com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity.class);
        given(places.find("12")).willReturn(Optional.of(place));
        given(ownership.ownerOf("12")).willReturn(Optional.of("owner"));
        given(messages.saveAndFlush(any())).willAnswer(inv -> {
            CenterChatMessageJpaEntity m = inv.getArgument(0);
            m.setId(906L);
            return m;
        });
        given(messages.countFrom(31L, "CUSTOMER", 900L)).willReturn(3L);
        given(members.displayNames(List.of("cust"))).willReturn(java.util.Map.of("cust", "김명상"));

        ChatMessageService.SendResult r = service.send(CUSTOMER, "31", CMID, "  세 번째  ", List.of());

        assertThat(r.created()).isTrue();
        assertThat(r.message().id()).isEqualTo("906");
        assertThat(r.message().mine()).isTrue();
        assertThat(r.message().body()).isEqualTo("세 번째");
        assertThat(r.message().clientMessageId()).isEqualTo(CMID);
        assertThat(t.getLastMessageId()).isEqualTo(906L);
        assertThat(t.getLastSenderRole()).isEqualTo("CUSTOMER");
        verify(notifier).message(eq(31L), eq(SenderRole.CUSTOMER), eq("owner"), any(), eq("김명상"), eq("세 번째"), eq(0), eq(3L));
        verify(events).publishEvent(new ChatEvents.MessageCreated(31L, 906L));
    }

    @Test
    @DisplayName("오너가 해제된 대화 전송 409 CENTER_CHAT_UNAVAILABLE(NO_OWNER), 저장 없음")
    void 오너_없음_전송() {
        given(access.lock(CUSTOMER, "31")).willReturn(t);
        given(messages.findByThreadIdAndSenderRoleAndClientMessageId(31L, "CUSTOMER", CMID)).willReturn(Optional.empty());
        given(places.find("12")).willReturn(Optional.of(org.mockito.Mockito.mock(com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity.class)));
        given(ownership.ownerOf("12")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(CUSTOMER, "31", CMID, "hi", List.of()))
                .isInstanceOf(com.meditationmap.shared.exception.DetailedDomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).getErrorCode()).isEqualTo(ErrorCode.CENTER_CHAT_UNAVAILABLE);
                    assertThat(((com.meditationmap.shared.exception.DetailedDomainException) e).getDetails()).containsEntry("reason", "NO_OWNER");
                });
        verify(messages, never()).saveAndFlush(any());
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("대화 읽음은 그 대화의 내 미확인 CENTER_CHAT_MESSAGE 알림을 읽음 처리한다(C3.7)")
    void 읽음_알림() {
        t.setLastMessageId(906L);
        given(access.load(CUSTOMER, "31")).willReturn(t);
        given(threads.raiseCustomerRead(31L, 906L)).willReturn(1);
        CenterChatThreadJpaEntity fresh = new CenterChatThreadJpaEntity();
        fresh.setCustomerLastReadMessageId(906L);
        given(threads.findById(31L)).willReturn(Optional.of(fresh));

        service.read(CUSTOMER, "31", null);

        verify(notificationReads).markReadByRef("cust", "CENTER_CHAT_MESSAGE", "CENTER_CHAT", "31");
    }

    @Test
    @DisplayName("차단된 대화 전송 409 CENTER_CHAT_BLOCKED, 본문·사진 모두 없으면 400")
    void 차단과_빈_메시지() {
        t.setStatus("BLOCKED");
        given(access.lock(CUSTOMER, "31")).willReturn(t);
        given(messages.findByThreadIdAndSenderRoleAndClientMessageId(anyLong(), anyString(), anyString())).willReturn(Optional.empty());
        assertCode(() -> service.send(CUSTOMER, "31", CMID, "hi", List.of()), ErrorCode.CENTER_CHAT_BLOCKED);
        assertCode(() -> service.send(CUSTOMER, "31", CMID, "   ", List.of()), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> service.send(CUSTOMER, "31", "not-a-uuid", "hi", List.of()), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("읽음 역행 금지: 저장값(905)보다 작은 900 요청은 바뀌지 않고(SSE 없음) 현재 값 905 를 돌려준다")
    void 읽음_역행() {
        t.setLastMessageId(906L);
        given(access.load(CUSTOMER, "31")).willReturn(t);
        given(threads.raiseCustomerRead(31L, 900L)).willReturn(0);
        CenterChatThreadJpaEntity fresh = new CenterChatThreadJpaEntity();
        fresh.setId(31L);
        fresh.setCustomerLastReadMessageId(905L);
        given(threads.findById(31L)).willReturn(Optional.of(fresh));
        given(messages.countFrom(31L, "OWNER", 905L)).willReturn(1L);

        ChatViews.ReadResult r = service.read(CUSTOMER, "31", "900");

        assertThat(r.lastReadMessageId()).isEqualTo("905");
        assertThat(r.unreadCount()).isEqualTo(1);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("읽음 요청이 마지막 메시지보다 크면 마지막 id 로 자른다")
    void 읽음_자르기() {
        t.setLastMessageId(906L);
        given(access.load(CUSTOMER, "31")).willReturn(t);
        given(threads.raiseCustomerRead(31L, 906L)).willReturn(1);
        CenterChatThreadJpaEntity fresh = new CenterChatThreadJpaEntity();
        fresh.setCustomerLastReadMessageId(906L);
        given(threads.findById(31L)).willReturn(Optional.of(fresh));

        assertThat(service.read(CUSTOMER, "31", "99999").lastReadMessageId()).isEqualTo("906");
        verify(events).publishEvent(new ChatEvents.ReadRaised(31L, "CUSTOMER", 906L));
    }

    @Test
    @DisplayName("소프트 삭제: 본인 메시지만, 원문은 남기고 응답은 deleted=true·body null·images [], 두 번째 호출도 200(멱등, 이벤트 1번)")
    void 소프트_삭제() {
        given(access.lock(CUSTOMER, "31")).willReturn(t);
        CenterChatMessageJpaEntity m = msg(905, "CUSTOMER", "cust");
        given(messages.findByIdAndThreadId(905L, 31L)).willReturn(Optional.of(m));

        ChatViews.Message first = service.delete(CUSTOMER, "31", "905");
        ChatViews.Message second = service.delete(CUSTOMER, "31", "905");

        assertThat(first.deleted()).isTrue();
        assertThat(first.body()).isNull();
        assertThat(first.images()).isEmpty();
        assertThat(second.deleted()).isTrue();
        assertThat(m.getBody()).as("원문은 DB 에 남는다").isEqualTo("안녕하세요");
        assertThat(m.getDeletedByMemberId()).isEqualTo("cust");
        verify(events, org.mockito.Mockito.times(1)).publishEvent(new ChatEvents.MessageDeleted(31L, 905L));
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("상대 메시지 삭제는 403 FORBIDDEN, 없는 메시지 404")
    void 남의_메시지_삭제() {
        given(access.lock(CUSTOMER, "31")).willReturn(t);
        given(messages.findByIdAndThreadId(906L, 31L)).willReturn(Optional.of(msg(906, "OWNER", "owner")));
        given(messages.findByIdAndThreadId(907L, 31L)).willReturn(Optional.empty());
        assertCode(() -> service.delete(CUSTOMER, "31", "906"), ErrorCode.FORBIDDEN);
        assertCode(() -> service.delete(CUSTOMER, "31", "907"), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 메시지 사진은 당사자에게 404")
    void 삭제된_사진() {
        given(access.load(CUSTOMER, "31")).willReturn(t);
        CenterChatMessageJpaEntity m = msg(905, "CUSTOMER", "cust");
        m.setImages(new ObjectMapper().createArrayNode().add(new ObjectMapper().createObjectNode().put("key", "private/x").put("contentType", "image/png")));
        m.setDeletedAt(Instant.parse("2026-10-08T01:00:00Z"));
        given(messages.findByIdAndThreadId(905L, 31L)).willReturn(Optional.of(m));
        assertCode(() -> service.image(CUSTOMER, "31", "905", "0"), ErrorCode.RESOURCE_NOT_FOUND);
    }

    private static void assertCode(Runnable r, ErrorCode c) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode()).isEqualTo(c);
    }
}
