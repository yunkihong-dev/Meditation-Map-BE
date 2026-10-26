package com.meditationmap.centerchat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.notification.application.NotificationCommandService;
import com.meditationmap.notification.application.NotificationDraft;
import com.meditationmap.notification.domain.LinkUrlPolicy;
import com.meditationmap.notification.domain.NotificationRefType;
import com.meditationmap.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 채팅 알림 합치기(C7.2): 합침 경로(replaceUnread)로 저장하고, 제목에 같은 대화 미확인 수 n(≥2)을 싣는다. linkUrl 은 C8 경로. */
@ExtendWith(MockitoExtension.class)
class ChatNotifierTest {

    @Mock private NotificationCommandService notifications;
    @Mock private MemberDirectory members;

    private NotificationDraft captured(String recipient) {
        ArgumentCaptor<NotificationDraft> c = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(notifications).replaceUnread(eq(recipient), c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("고객 3번째 메시지 → 오너에게 '새 문의 메시지 3개' 1건(합침 경로), 링크 /center/inquiries/{t}")
    void 고객_메시지_합침() {
        new ChatNotifier(notifications, members)
                .message(31L, SenderRole.CUSTOMER, "owner", "고요명상센터", "김명상", "주말\n자리 있나요?", 0, 3);

        NotificationDraft d = captured("owner");
        assertThat(d.type()).isEqualTo(NotificationType.CENTER_CHAT_MESSAGE);
        assertThat(d.title()).isEqualTo("새 문의 메시지 3개");
        assertThat(d.body()).isEqualTo("[고요명상센터] 김명상: 주말 자리 있나요?");
        assertThat(d.linkUrl()).isEqualTo("/center/inquiries/31");
        assertThat(d.refType()).isEqualTo(NotificationRefType.CENTER_CHAT);
        assertThat(d.refId()).isEqualTo("31");
        assertThat(LinkUrlPolicy.isAllowed(d.linkUrl())).isTrue();
    }

    @Test
    @DisplayName("첫 메시지는 n 표시 없음, 오너 답장(사진만)은 고객에게 '사진을 보냈어요' + /profile/center-chats/{t}")
    void 단건과_오너_답장() {
        ChatNotifier n = new ChatNotifier(notifications, members);
        n.message(31L, SenderRole.CUSTOMER, "owner", "고요명상센터", "김명상", "안녕하세요", 0, 1);
        assertThat(captured("owner").title()).isEqualTo("새 문의 메시지");

        n.message(31L, SenderRole.OWNER, "cust", "고요명상센터", "김명상", null, 2, 2);
        NotificationDraft d = captured("cust");
        assertThat(d.title()).isEqualTo("'고요명상센터'에서 답장이 왔어요 (2)");
        assertThat(d.body()).isEqualTo("사진을 보냈어요");
        assertThat(d.linkUrl()).isEqualTo("/profile/center-chats/31");
        assertThat(LinkUrlPolicy.isAllowed(d.linkUrl())).isTrue();
    }
}
