package com.meditationmap.centerchat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.meditationmap.centerchat.domain.InquirySettingsPolicy.KakaoType;
import com.meditationmap.centerchat.domain.InquirySettingsPolicy.Mode;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadSpringDataRepository;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** 대화 시작(C3.1) — open_key 동시 시작에서 진 쪽은 유니크 충돌을 잡아 기존 대화로 200(500 금지). */
@ExtendWith(MockitoExtension.class)
class ChatStartServiceTest {

    @Mock private CenterChatThreadSpringDataRepository threads;
    @Mock private ChatPlaceLookup places;
    @Mock private PlaceOwnership ownership;
    @Mock private ChatAssembler assembler;
    @Mock private ChatStartService.ThreadInserter inserter;
    @Mock private InquirySettingsService inquirySettings;
    private ChatStartService service;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ChatStartService(threads, places, ownership, assembler, inserter, inquirySettings);
    }

    private PlaceJpaEntity place(boolean hidden) {
        PlaceJpaEntity p = new PlaceJpaEntity();
        p.setId("12");
        p.setData(om.createObjectNode().put("name", "고요").put("hidden", hidden)
                .set("programs", om.createArrayNode()
                        .add(om.createObjectNode().put("id", "p-1").put("title", "아침 명상"))
                        .add(om.createObjectNode().put("id", "p-h").put("hidden", true))));
        return p;
    }

    @Test
    @DisplayName("동시 시작에서 INSERT 유니크 충돌이면 기존 대화를 다시 읽어 200(created=false)")
    void 동시_시작_충돌() {
        given(places.find("12")).willReturn(Optional.of(place(false)));
        given(ownership.ownerOf("12")).willReturn(Optional.of("owner"));
        CenterChatThreadJpaEntity existing = new CenterChatThreadJpaEntity();
        existing.setId(31L);
        existing.setStatus("OPEN");
        given(threads.findByOpenKey("12|cust|-")).willReturn(Optional.empty(), Optional.of(existing));
        given(inquirySettings.effective("12", Optional.of("owner"))).willReturn(InquirySettingsService.Effective.DEFAULT);
        given(inserter.insert(eq("12"), eq("cust"), any(), any(), eq("12|cust|-")))
                .willThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_center_chat_threads_open_key'"));

        ChatStartService.StartResult r = service.start("cust", "12", null);

        assertThat(r.created()).isFalse();
    }

    @Test
    @DisplayName("오너 없음 409 NO_OWNER, 숨김 409 HIDDEN, 내 장소 400, 숨김 프로그램 400, 차단된 대화 409 + threadId")
    void 거부_사례() {
        given(places.find("12")).willReturn(Optional.of(place(false)));
        given(ownership.ownerOf("12")).willReturn(Optional.empty());
        assertDetail(() -> service.start("cust", "12", null), ErrorCode.CENTER_CHAT_UNAVAILABLE, "reason", "NO_OWNER");

        given(ownership.ownerOf("12")).willReturn(Optional.of("owner"));
        given(places.find("12")).willReturn(Optional.of(place(true)));
        assertDetail(() -> service.start("cust", "12", null), ErrorCode.CENTER_CHAT_UNAVAILABLE, "reason", "HIDDEN");

        given(places.find("12")).willReturn(Optional.of(place(false)));
        assertCode(() -> service.start("owner", "12", null), ErrorCode.CENTER_CHAT_SELF_INQUIRY);
        assertCode(() -> service.start("cust", "12", TextNode.valueOf("p-h")), ErrorCode.CENTER_CHAT_PROGRAM_NOT_FOUND);

        CenterChatThreadJpaEntity blocked = new CenterChatThreadJpaEntity();
        blocked.setId(40L);
        blocked.setStatus("BLOCKED");
        given(threads.findByOpenKey(anyString())).willReturn(Optional.of(blocked));
        assertDetail(() -> service.start("cust", "12", TextNode.valueOf("p-1")), ErrorCode.CENTER_CHAT_BLOCKED, "threadId", "40");
    }

    @Test
    @DisplayName("programId 가 문자열이 아니면 400 VALIDATION_FAILED")
    void 프로그램_타입() {
        assertCode(() -> service.start("cust", "12", om.getNodeFactory().numberNode(3)), ErrorCode.VALIDATION_FAILED);
    }

    private static final InquirySettingsService.Effective KAKAO =
            new InquirySettingsService.Effective(Mode.KAKAO, KakaoType.OPEN_CHAT, "https://open.kakao.com/o/gAbc123");

    @Test
    @DisplayName("C17: KAKAO 모드면 새 대화 시작 409 CENTER_CHAT_UNAVAILABLE reason=KAKAO, INSERT 안 함")
    void 카카오_새_대화_409() {
        given(places.find("12")).willReturn(Optional.of(place(false)));
        given(ownership.ownerOf("12")).willReturn(Optional.of("owner"));
        given(threads.findByOpenKey("12|cust|-")).willReturn(Optional.empty());
        given(inquirySettings.effective("12", Optional.of("owner"))).willReturn(KAKAO);

        assertDetail(() -> service.start("cust", "12", null), ErrorCode.CENTER_CHAT_UNAVAILABLE, "reason", "KAKAO");
        verify(inserter, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("C17: KAKAO 모드여도 이미 있는 대화는 200(created=false)으로 돌려준다 — 진행 중 대화 유지")
    void 카카오_기존_대화_200() {
        given(places.find("12")).willReturn(Optional.of(place(false)));
        given(ownership.ownerOf("12")).willReturn(Optional.of("owner"));
        CenterChatThreadJpaEntity existing = new CenterChatThreadJpaEntity();
        existing.setId(31L);
        existing.setStatus("OPEN");
        given(threads.findByOpenKey("12|cust|-")).willReturn(Optional.of(existing));

        ChatStartService.StartResult r = service.start("cust", "12", null);

        assertThat(r.created()).isFalse();
        verify(inserter, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("C17: 오너 없음·숨김 판정이 KAKAO 보다 먼저")
    void 오너없음_숨김_우선() {
        given(places.find("12")).willReturn(Optional.of(place(false)));
        given(ownership.ownerOf("12")).willReturn(Optional.empty());
        assertDetail(() -> service.start("cust", "12", null), ErrorCode.CENTER_CHAT_UNAVAILABLE, "reason", "NO_OWNER");

        given(ownership.ownerOf("12")).willReturn(Optional.of("owner"));
        given(places.find("12")).willReturn(Optional.of(place(true)));
        assertDetail(() -> service.start("cust", "12", null), ErrorCode.CENTER_CHAT_UNAVAILABLE, "reason", "HIDDEN");
    }

    private static void assertCode(Runnable r, ErrorCode c) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode()).isEqualTo(c);
    }

    private static void assertDetail(Runnable r, ErrorCode c, String key, String value) {
        assertThatThrownBy(r::run).isInstanceOf(DetailedDomainException.class).satisfies(e -> {
            assertThat(((DetailedDomainException) e).getErrorCode()).isEqualTo(c);
            assertThat(((DetailedDomainException) e).getDetails()).containsEntry(key, value);
        });
    }
}
