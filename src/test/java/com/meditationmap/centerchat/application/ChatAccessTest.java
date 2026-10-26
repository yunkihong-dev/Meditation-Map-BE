package com.meditationmap.centerchat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.meditationmap.centerchat.application.ChatAccess.Viewer;
import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatReportSpringDataRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadSpringDataRepository;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 채팅 접근 판정(C0, DC2) — 실패는 전부 404. */
@ExtendWith(MockitoExtension.class)
class ChatAccessTest {

    @Mock private CenterChatThreadSpringDataRepository threads;
    @Mock private CenterChatReportSpringDataRepository reports;
    @Mock private PlaceOwnership ownership;
    private ChatAccess access;
    private final CenterChatThreadJpaEntity t = new CenterChatThreadJpaEntity();

    @BeforeEach
    void setUp() {
        access = new ChatAccess(threads, reports, ownership);
        t.setId(31L);
        t.setPlaceId("12");
        t.setCustomerMemberId("cust");
    }

    @Test
    @DisplayName("고객: 본인 대화만, 남의 대화는 404")
    void 고객() {
        given(threads.findById(31L)).willReturn(Optional.of(t));
        assertThat(access.load(new Viewer("cust", SenderRole.CUSTOMER), "31")).isSameAs(t);
        assertNotFound(() -> access.load(new Viewer("other", SenderRole.CUSTOMER), "31"));
    }

    @Test
    @DisplayName("오너: 지금 오너면 통과(새 오너는 이전 대화를 본다), 이전 오너는 즉시 404 (DC2)")
    void 오너_교체() {
        given(threads.findById(31L)).willReturn(Optional.of(t));
        given(ownership.isOwner("newOwner", "12")).willReturn(true);
        given(ownership.isOwner("oldOwner", "12")).willReturn(false);
        assertThat(access.load(new Viewer("newOwner", SenderRole.OWNER), "31")).isSameAs(t);
        assertNotFound(() -> access.load(new Viewer("oldOwner", SenderRole.OWNER), "31"));
    }

    @Test
    @DisplayName("관리자: 신고 없는 대화는 404, 있으면 통과 / 숫자 아닌 id 404")
    void 관리자() {
        given(threads.findById(31L)).willReturn(Optional.of(t));
        given(reports.existsByThreadId(31L)).willReturn(false, true);
        assertNotFound(() -> access.loadForAdmin("31"));
        assertThat(access.loadForAdmin("31")).isSameAs(t);
        assertNotFound(() -> access.loadForAdmin("abc"));
    }

    private static void assertNotFound(Runnable r) {
        assertThatThrownBy(r::run)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
