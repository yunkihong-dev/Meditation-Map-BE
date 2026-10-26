package com.meditationmap.centerchat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.centerchat.application.ChatAccess.Viewer;
import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.centerchat.infrastructure.jdbc.CenterChatJdbcRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatMessageJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatMessageSpringDataRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadJpaEntity;
import com.meditationmap.identity.application.MemberDirectory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 응답 조립 — 삭제 표시(당사자)·원문(관리자), 경로별 사진 URL, 미답변 정의(삭제된 마지막 메시지 포함). */
@ExtendWith(MockitoExtension.class)
class ChatAssemblerTest {

    @Mock private ChatPlaceLookup places;
    @Mock private MemberDirectory members;
    @Mock private CenterChatMessageSpringDataRepository messages;
    @Mock private CenterChatJdbcRepository jdbc;

    private CenterChatMessageJpaEntity withImage(boolean deleted) {
        CenterChatMessageJpaEntity m = new CenterChatMessageJpaEntity();
        m.setId(905L);
        m.setThreadId(31L);
        m.setSenderRole("CUSTOMER");
        m.setSenderMemberId("cust");
        m.setBody("원문");
        m.setClientMessageId("c");
        ObjectMapper om = new ObjectMapper();
        m.setImages(om.createArrayNode().add(om.createObjectNode().put("key", "private/center-chat/k.jpg").put("contentType", "image/jpeg").put("sizeBytes", 10)));
        m.setCreatedAt(Instant.parse("2026-10-08T01:20:00Z"));
        if (deleted) {
            m.setDeletedAt(Instant.parse("2026-10-08T01:21:00Z"));
        }
        return m;
    }

    @Test
    @DisplayName("당사자: 사진 URL 은 보는 경로별(고객 /me/center-chats, 오너 /me/center-inbox), S3 키 없음, clientMessageId 는 mine 일 때만")
    void 경로별_URL() {
        ChatAssembler a = new ChatAssembler(places, members, messages, jdbc);
        ChatViews.Message c = a.message(new Viewer("cust", SenderRole.CUSTOMER), withImage(false));
        ChatViews.Message o = a.message(new Viewer("own", SenderRole.OWNER), withImage(false));
        assertThat(c.images().get(0).url()).isEqualTo("/me/center-chats/31/messages/905/images/0");
        assertThat(o.images().get(0).url()).isEqualTo("/me/center-inbox/31/messages/905/images/0");
        assertThat(c.mine()).isTrue();
        assertThat(c.clientMessageId()).isEqualTo("c");
        assertThat(o.clientMessageId()).isNull();
        assertThat(c.toString() + o.toString()).doesNotContain("private/");
    }

    @Test
    @DisplayName("삭제된 메시지: 당사자에게는 deleted=true·body null·images [], 관리자에게는 원문·사진·deleted=true·deletedAt")
    void 삭제_표시와_관리자_원문() {
        ChatAssembler a = new ChatAssembler(places, members, messages, jdbc);
        given(members.findBriefs(any())).willReturn(Map.of("cust", new MemberDirectory.MemberBrief("cust", "c1@example.com", null, null)));

        ChatViews.Message party = a.message(new Viewer("own", SenderRole.OWNER), withImage(true));
        ChatViews.AdminMessage admin = a.adminMessages(List.of(withImage(true))).get(0);

        assertThat(party.deleted()).isTrue();
        assertThat(party.body()).isNull();
        assertThat(party.images()).isEmpty();
        assertThat(admin.deleted()).isTrue();
        assertThat(admin.body()).isEqualTo("원문");
        assertThat(admin.images().get(0).url()).isEqualTo("/admin/center-chats/31/messages/905/images/0");
        assertThat(admin.deletedAt()).isEqualTo("2026-10-08T01:21:00.000Z");
        assertThat(admin.sender().email()).isEqualTo("c1@example.com");
    }

    @Test
    @DisplayName("미답변: OPEN + 마지막 보낸 쪽 CUSTOMER — 마지막 메시지가 삭제됐어도 미답변으로 센다(C9). 차단·오너 답장은 아님")
    void 미답변() {
        CenterChatThreadJpaEntity t = new CenterChatThreadJpaEntity();
        t.setStatus("OPEN");
        t.setLastSenderRole("CUSTOMER");
        assertThat(ChatAssembler.unanswered(t)).isTrue();
        t.setLastSenderRole("OWNER");
        assertThat(ChatAssembler.unanswered(t)).isFalse();
        t.setLastSenderRole("CUSTOMER");
        t.setStatus("BLOCKED");
        assertThat(ChatAssembler.unanswered(t)).isFalse();
        t.setStatus("OPEN");
        t.setLastSenderRole(null);
        assertThat(ChatAssembler.unanswered(t)).as("메시지 0개").isFalse();

        ChatViews.LastMessage lm = ChatAssembler.lastMessage(withImage(true));
        assertThat(lm.deleted()).isTrue();
        assertThat(lm.preview()).isNull();
        assertThat(lm.imageCount()).isZero();
    }

    @Test
    @DisplayName("C15 deletable: 오너 이관 전 이전 오너 true → 이관 뒤 새 오너는 mine:true 지만 false, 고객 쪽 false, 삭제된 메시지 false")
    void deletable_이관_전후() {
        ChatAssembler a = new ChatAssembler(places, members, messages, jdbc);
        CenterChatMessageJpaEntity byOldOwner = withImage(false);
        byOldOwner.setSenderRole("OWNER");
        byOldOwner.setSenderMemberId("owner5");

        ChatViews.Message before = a.message(new Viewer("owner5", SenderRole.OWNER), byOldOwner);
        assertThat(before.mine()).isTrue();
        assertThat(before.deletable()).isTrue();

        ChatViews.Message after = a.message(new Viewer("owner9", SenderRole.OWNER), byOldOwner);
        assertThat(after.mine()).isTrue();
        assertThat(after.deletable()).isFalse();

        assertThat(a.message(new Viewer("cust", SenderRole.CUSTOMER), byOldOwner).deletable()).isFalse();

        CenterChatMessageJpaEntity mineDeleted = withImage(true);
        ChatViews.Message d = a.message(new Viewer("cust", SenderRole.CUSTOMER), mineDeleted);
        assertThat(d.mine()).isTrue();
        assertThat(d.deleted()).isTrue();
        assertThat(d.deletable()).isFalse();
        assertThat(a.message(new Viewer("cust", SenderRole.CUSTOMER), withImage(false)).deletable()).isTrue();
    }
}
