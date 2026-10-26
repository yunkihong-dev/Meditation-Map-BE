package com.meditationmap.centerchat.application;

import com.meditationmap.centerchat.application.ChatAccess.Viewer;
import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.centerchat.infrastructure.jdbc.CenterChatJdbcRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadSpringDataRepository;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 고객 목록·오너 문의함·요약·안 읽은 수(채팅 C3.2·C3.3·C4.1·C4.2). 메시지 0개 대화는 나오지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatInboxService {

    private final CenterChatThreadSpringDataRepository threads;
    private final CenterChatJdbcRepository jdbc;
    private final PlaceOwnership ownership;
    private final ChatPlaceLookup places;
    private final ChatAssembler assembler;

    public ChatViews.Page<ChatViews.Thread> customerList(String memberId, String cursorRaw, Integer sizeRaw) {
        int size = size(sizeRaw);
        List<CenterChatThreadJpaEntity> rows = threads.customerPage(memberId, cursor(cursorRaw), PageRequest.of(0, size + 1));
        return page(new Viewer(memberId, SenderRole.CUSTOMER), rows, size);
    }

    public ChatViews.UnreadCount customerUnread(String memberId) {
        long[] v = jdbc.customerUnread(memberId);
        return new ChatViews.UnreadCount(v[0], v[1]);
    }

    public ChatViews.Page<ChatViews.Thread> ownerList(
            String memberId, String placeIdRaw, String unansweredRaw, String cursorRaw, Integer sizeRaw) {
        boolean unanswered;
        if (unansweredRaw == null || unansweredRaw.isBlank() || "false".equalsIgnoreCase(unansweredRaw) || "0".equals(unansweredRaw)) {
            unanswered = false;
        } else if ("true".equalsIgnoreCase(unansweredRaw) || "1".equals(unansweredRaw)) {
            unanswered = true;
        } else {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
        int size = size(sizeRaw);
        List<String> scope = scope(memberId, placeIdRaw);
        if (scope.isEmpty()) {
            return new ChatViews.Page<>(List.of(), null);
        }
        List<CenterChatThreadJpaEntity> rows =
                threads.ownerPage(scope, unanswered, cursor(cursorRaw), PageRequest.of(0, size + 1));
        return page(new Viewer(memberId, SenderRole.OWNER), rows, size);
    }

    public ChatViews.Summary summary(String memberId, String placeIdRaw) {
        List<String> scope = scope(memberId, placeIdRaw);
        long[] v = jdbc.ownerSummary(scope);
        String placeId = placeIdRaw == null || placeIdRaw.isBlank() ? null : placeIdRaw.trim();
        return new ChatViews.Summary(placeId, v[0], v[1], v[2]);
    }

    /** 장소 필터: 생략이면 내 장소 전부. 남의 장소 403 PLACE_NOT_OWNER, 없는 장소 404. */
    private List<String> scope(String memberId, String placeIdRaw) {
        if (placeIdRaw == null || placeIdRaw.isBlank()) {
            return ownership.placeIdsOwnedBy(memberId);
        }
        String placeId = placeIdRaw.trim();
        places.find(placeId).orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!ownership.isOwner(memberId, placeId)) {
            throw new DomainArgumentException(ErrorCode.PLACE_NOT_OWNER);
        }
        return List.of(placeId);
    }

    private ChatViews.Page<ChatViews.Thread> page(Viewer viewer, List<CenterChatThreadJpaEntity> rows, int size) {
        boolean more = rows.size() > size;
        List<CenterChatThreadJpaEntity> page = rows.stream().limit(size).toList();
        String next = more && !page.isEmpty() ? String.valueOf(page.get(page.size() - 1).getLastMessageId()) : null;
        return new ChatViews.Page<>(assembler.threads(viewer, page), next);
    }

    static Long cursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long v = Long.parseLong(raw.trim());
            if (v <= 0) {
                throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
    }

    static int size(Integer raw) {
        int s = raw == null ? 20 : raw;
        if (s < 1) {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
        return Math.min(s, 50);
    }
}
