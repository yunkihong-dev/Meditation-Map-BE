package com.meditationmap.centerchat.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meditationmap.centerchat.application.ChatAccess.Viewer;
import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatMessageSpringDataRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadSpringDataRepository;
import com.meditationmap.notification.application.port.out.RealtimeNotifier;
import com.meditationmap.placeowner.application.PlaceOwnership;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 채팅 SSE(C7.1) — 커밋 뒤 비동기. 받는 사람(고객·현재 오너)마다 그 사람 기준 payload 를 만든다(mine·images[].url·
 * clientMessageId 가 역할마다 다르다). 연결이 없으면 버린다(오프라인은 알림이 맡는다). id: 줄은 쓰지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRealtimeListener {

    private final CenterChatThreadSpringDataRepository threads;
    private final CenterChatMessageSpringDataRepository messages;
    private final PlaceOwnership ownership;
    private final ChatAssembler assembler;
    private final RealtimeNotifier realtime;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ThreadSummary(String lastMessageId, long unreadCount, boolean unanswered, String status) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MessageEvent(String threadId, String viewerRole, ChatViews.Message message, ThreadSummary thread) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DeletedEvent(String threadId, String viewerRole, String messageId, ThreadSummary thread) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReadEvent(String threadId, String readerRole, String lastReadMessageId) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ThreadEvent(String threadId, String status) {}

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessage(ChatEvents.MessageCreated e) {
        threads.findById(e.threadId()).ifPresent(t -> messages.findById(e.messageId()).ifPresent(m -> {
            for (Viewer v : parties(t)) {
                ChatViews.Thread tv = assembler.thread(v, t);
                send(v, "chat-message", new MessageEvent(
                        String.valueOf(t.getId()), v.role().name(), assembler.message(v, m), summary(tv)));
            }
        }));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDeleted(ChatEvents.MessageDeleted e) {
        threads.findById(e.threadId()).ifPresent(t -> {
            for (Viewer v : parties(t)) {
                send(v, "chat-message-deleted", new DeletedEvent(
                        String.valueOf(t.getId()), v.role().name(), String.valueOf(e.messageId()), summary(assembler.thread(v, t))));
            }
        });
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRead(ChatEvents.ReadRaised e) {
        threads.findById(e.threadId()).ifPresent(t -> {
            ReadEvent ev = new ReadEvent(String.valueOf(t.getId()), e.readerRole(), String.valueOf(e.lastReadMessageId()));
            for (Viewer v : parties(t)) {
                send(v, "chat-read", ev);
            }
        });
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onThread(ChatEvents.ThreadStatusChanged e) {
        threads.findById(e.threadId()).ifPresent(t -> {
            for (Viewer v : parties(t)) {
                send(v, "chat-thread", new ThreadEvent(String.valueOf(t.getId()), e.status()));
            }
        });
    }

    private List<Viewer> parties(CenterChatThreadJpaEntity t) {
        List<Viewer> out = new ArrayList<>();
        out.add(new Viewer(t.getCustomerMemberId(), SenderRole.CUSTOMER));
        ownership.ownerOf(t.getPlaceId()).ifPresent(o -> out.add(new Viewer(o, SenderRole.OWNER)));
        return out;
    }

    private static ThreadSummary summary(ChatViews.Thread tv) {
        return new ThreadSummary(
                tv.lastMessage() == null ? null : tv.lastMessage().id(), tv.unreadCount(), tv.unanswered(), tv.status());
    }

    private void send(Viewer v, String event, Object data) {
        try {
            realtime.publishEvent(v.memberId(), event, data);
        } catch (RuntimeException ex) {
            log.warn("[CHAT] SSE {} 전송 실패 member={}", event, v.memberId(), ex);
        }
    }
}
