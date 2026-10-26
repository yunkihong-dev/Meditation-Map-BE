package com.meditationmap.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.meditationmap.notification.domain.NotificationRefType;
import com.meditationmap.notification.domain.NotificationType;
import com.meditationmap.notification.infrastructure.jpa.NotificationJpaEntity;
import com.meditationmap.notification.infrastructure.jpa.NotificationSpringDataRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** 채팅 알림 합치기(C7.2-1): 같은 수신자·type·ref 의 미확인 행을 먼저 지우고 새 행 1개를 넣는다. 전달 이벤트는 새 행 id 로. */
@ExtendWith(MockitoExtension.class)
class NotificationCommandServiceReplaceTest {

    @Mock private NotificationSpringDataRepository repo;
    @Mock private ApplicationEventPublisher events;

    @Test
    @DisplayName("replaceUnread: deleteUnreadByRef(수신자, CENTER_CHAT_MESSAGE, CENTER_CHAT, threadId) → saveAll 1행 → NotificationsCreated")
    void 지우고_하나_넣기() {
        NotificationCommandService service =
                new NotificationCommandService(repo, events, Clock.fixed(Instant.parse("2026-10-08T01:00:00Z"), ZoneOffset.UTC));
        given(repo.saveAll(anyList())).willAnswer(inv -> {
            List<NotificationJpaEntity> rows = inv.getArgument(0);
            rows.get(0).setId(77L);
            return rows;
        });

        List<Long> ids = service.replaceUnread(
                "owner",
                new NotificationDraft(NotificationType.CENTER_CHAT_MESSAGE, "새 문의 메시지 2개", "[센터] 김: 안녕",
                        "/center/inquiries/31", NotificationRefType.CENTER_CHAT, "31"));

        assertThat(ids).containsExactly(77L);
        InOrder order = inOrder(repo, events);
        order.verify(repo).deleteUnreadByRef("owner", "CENTER_CHAT_MESSAGE", "CENTER_CHAT", "31");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationJpaEntity>> rows = ArgumentCaptor.forClass(List.class);
        order.verify(repo).saveAll(rows.capture());
        assertThat(rows.getValue()).hasSize(1);
        assertThat(rows.getValue().get(0).getRecipientMemberId()).isEqualTo("owner");
        assertThat(rows.getValue().get(0).getTitle()).isEqualTo("새 문의 메시지 2개");
        assertThat(rows.getValue().get(0).getReadAt()).isNull();
        order.verify(events).publishEvent(new NotificationEvents.NotificationsCreated(List.of(77L)));
    }
}
