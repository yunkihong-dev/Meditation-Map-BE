package com.meditationmap.centerchat.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.centerchat.application.ChatAccess.Viewer;
import com.meditationmap.centerchat.domain.ChatEnums;
import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.centerchat.domain.ChatEnums.ThreadStatus;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadSpringDataRepository;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화 시작(get-or-create, 채팅 C3.1). 같은 고객·장소·프로그램(null 포함)의 대화는 open_key 유니크로 1개다. 동시 시작에서 진
 * 쪽은 유니크 충돌을 잡아 기존 대화를 다시 읽어 200 으로 돌려준다(500 금지 — C9).
 */
@Service
@RequiredArgsConstructor
public class ChatStartService {

    private final CenterChatThreadSpringDataRepository threads;
    private final ChatPlaceLookup places;
    private final PlaceOwnership ownership;
    private final ChatAssembler assembler;
    private final ThreadInserter inserter;
    private final InquirySettingsService inquirySettings;

    public record StartResult(boolean created, ChatViews.Thread thread) {}

    public StartResult start(String customerId, String placeIdRaw, JsonNode programIdNode) {
        String placeId = placeIdRaw == null ? null : placeIdRaw.trim();
        String programId = null;
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        if (placeId == null || placeId.isEmpty() || placeId.length() > 255) {
            errors.put("placeId", "필수입니다.");
        }
        if (programIdNode != null && !programIdNode.isNull()) {
            if (!programIdNode.isTextual() || programIdNode.asText().isBlank() || programIdNode.asText().length() > 100) {
                errors.put("programId", "100자 이하 문자열이어야 합니다.");
            } else {
                programId = programIdNode.asText();
            }
        }
        if (!errors.isEmpty()) {
            throw DetailedDomainException.fields(ErrorCode.VALIDATION_FAILED, errors);
        }
        PlaceJpaEntity place = places.find(placeId).orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        Optional<String> owner = ownership.ownerOf(placeId);
        if (owner.isEmpty()) {
            throw new DetailedDomainException(ErrorCode.CENTER_CHAT_UNAVAILABLE, Map.of("reason", "NO_OWNER"));
        }
        if (ChatPlaceLookup.hidden(place)) {
            throw new DetailedDomainException(ErrorCode.CENTER_CHAT_UNAVAILABLE, Map.of("reason", "HIDDEN"));
        }
        if (owner.get().equals(customerId)) {
            throw new DomainArgumentException(ErrorCode.CENTER_CHAT_SELF_INQUIRY);
        }
        String programTitle = null;
        if (programId != null) {
            JsonNode prog =
                    ChatPlaceLookup.visibleProgram(place, programId)
                            .orElseThrow(() -> new DomainArgumentException(ErrorCode.CENTER_CHAT_PROGRAM_NOT_FOUND));
            programTitle = prog.path("title").isTextual() ? prog.get("title").asText() : null;
        }

        String key = ChatEnums.openKey(placeId, customerId, programId);
        Viewer viewer = new Viewer(customerId, SenderRole.CUSTOMER);
        Optional<CenterChatThreadJpaEntity> existing = threads.findByOpenKey(key);
        boolean created = false;
        CenterChatThreadJpaEntity t;
        if (existing.isPresent()) {
            // 문의 방식이 KAKAO 여도 이미 있는 대화는 그대로 돌려준다(사용자 결정 2026-10-15: 진행 중 대화는 계속).
            t = existing.get();
        } else {
            // C17: KAKAO 면 새 대화만 막는다. 오너 없음·숨김(위)이 먼저 판정된다.
            if (inquirySettings.effective(placeId, owner).kakao()) {
                throw new DetailedDomainException(ErrorCode.CENTER_CHAT_UNAVAILABLE, Map.of("reason", "KAKAO"));
            }
            try {
                t = inserter.insert(placeId, customerId, programId, programTitle, key);
                created = true;
            } catch (DataIntegrityViolationException race) {
                t = threads.findByOpenKey(key).orElseThrow(() -> race);
            }
        }
        if (ThreadStatus.BLOCKED.name().equals(t.getStatus())) {
            throw new DetailedDomainException(ErrorCode.CENTER_CHAT_BLOCKED, Map.of("threadId", String.valueOf(t.getId())));
        }
        return new StartResult(created, assembler.thread(viewer, t));
    }

    /** INSERT 를 별도 트랜잭션으로 — 유니크 충돌이 바깥(조회) 흐름을 롤백 전용으로 만들지 않게. */
    @Service
    @RequiredArgsConstructor
    public static class ThreadInserter {
        private final CenterChatThreadSpringDataRepository threads;
        private final Clock clock;

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public CenterChatThreadJpaEntity insert(
                String placeId, String customerId, String programId, String programTitle, String key) {
            CenterChatThreadJpaEntity t = new CenterChatThreadJpaEntity();
            t.setPlaceId(placeId);
            t.setCustomerMemberId(customerId);
            t.setProgramId(programId);
            t.setProgramTitle(programTitle);
            t.setStatus(ThreadStatus.OPEN.name());
            t.setOpenKey(key);
            t.setCreatedAt(clock.instant());
            return threads.saveAndFlush(t);
        }
    }
}
