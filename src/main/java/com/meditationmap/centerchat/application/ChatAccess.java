package com.meditationmap.centerchat.application;

import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatReportSpringDataRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadSpringDataRepository;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 접근 판정(채팅 C0). 실패는 전부 404 — 남의 대화가 있는지 드러내지 않는다.
 *
 * <ul>
 *   <li>고객: 대화의 customer_member_id = 나
 *   <li>오너: <b>지금</b> 그 장소의 오너 = 나(대화에 오너를 저장하지 않으므로 이전 오너는 즉시 404, 새 오너는 이전 대화를 본다 — DC2)
 *   <li>관리자: 그 대화에 신고가 1건 이상
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ChatAccess {

    private final CenterChatThreadSpringDataRepository threads;
    private final CenterChatReportSpringDataRepository reports;
    private final PlaceOwnership ownership;

    /** 보는 사람. role 은 고객/오너 경로가 정한다. */
    public record Viewer(String memberId, SenderRole role) {
        public String basePath() {
            return role == SenderRole.CUSTOMER ? "/me/center-chats" : "/me/center-inbox";
        }
    }

    public CenterChatThreadJpaEntity load(Viewer viewer, String threadIdRaw) {
        return check(viewer, threads.findById(parseId(threadIdRaw)).orElseThrow(ChatAccess::notFound));
    }

    /** 대화 행 FOR UPDATE 후 판정(전송·삭제 직렬화). 호출자 트랜잭션 안에서. */
    public CenterChatThreadJpaEntity lock(Viewer viewer, String threadIdRaw) {
        return check(viewer, threads.findByIdForUpdate(parseId(threadIdRaw)).orElseThrow(ChatAccess::notFound));
    }

    public CenterChatThreadJpaEntity loadForAdmin(String threadIdRaw) {
        CenterChatThreadJpaEntity t = threads.findById(parseId(threadIdRaw)).orElseThrow(ChatAccess::notFound);
        if (!reports.existsByThreadId(t.getId())) {
            throw notFound();
        }
        return t;
    }

    private CenterChatThreadJpaEntity check(Viewer viewer, CenterChatThreadJpaEntity t) {
        boolean ok =
                viewer.role() == SenderRole.CUSTOMER
                        ? t.getCustomerMemberId().equals(viewer.memberId())
                        : ownership.isOwner(viewer.memberId(), t.getPlaceId());
        if (!ok) {
            throw notFound();
        }
        return t;
    }

    public static Long parseId(String raw) {
        try {
            long v = Long.parseLong(raw);
            if (v <= 0) {
                throw notFound();
            }
            return v;
        } catch (NumberFormatException e) {
            throw notFound();
        }
    }

    static DomainArgumentException notFound() {
        return new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
