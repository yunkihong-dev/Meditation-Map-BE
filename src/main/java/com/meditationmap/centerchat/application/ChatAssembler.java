package com.meditationmap.centerchat.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.centerchat.application.ChatAccess.Viewer;
import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.centerchat.domain.ChatEnums.ThreadStatus;
import com.meditationmap.centerchat.infrastructure.jdbc.CenterChatJdbcRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatMessageJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatMessageSpringDataRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadJpaEntity;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.shared.domain.ApiTimestamps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 엔티티 → 응답(보는 사람 기준). 목록은 장소·이름·마지막 메시지·안 읽은 수를 묶음 조회한다(N+1 없음). */
@Component
@RequiredArgsConstructor
public class ChatAssembler {

    static final int PREVIEW = 80;

    private final ChatPlaceLookup places;
    private final MemberDirectory members;
    private final CenterChatMessageSpringDataRepository messages;
    private final CenterChatJdbcRepository jdbc;

    public ChatViews.Thread thread(Viewer viewer, CenterChatThreadJpaEntity t) {
        return threads(viewer, List.of(t)).get(0);
    }

    public List<ChatViews.Thread> threads(Viewer viewer, List<CenterChatThreadJpaEntity> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<String, PlaceJpaEntity> placeMap = places.findAll(list.stream().map(CenterChatThreadJpaEntity::getPlaceId).toList());
        Map<String, String> names = members.displayNames(list.stream().map(CenterChatThreadJpaEntity::getCustomerMemberId).toList());
        Map<Long, CenterChatMessageJpaEntity> last = new java.util.HashMap<>();
        for (CenterChatMessageJpaEntity m :
                messages.findAllById(list.stream().map(CenterChatThreadJpaEntity::getLastMessageId).filter(java.util.Objects::nonNull).toList())) {
            last.put(m.getId(), m);
        }
        Map<Long, Long> unread = jdbc.unreadByThread(list.stream().map(CenterChatThreadJpaEntity::getId).toList(), viewer.role());
        List<ChatViews.Thread> out = new ArrayList<>();
        for (CenterChatThreadJpaEntity t : list) {
            PlaceJpaEntity p = placeMap.get(t.getPlaceId());
            CenterChatMessageJpaEntity lm = t.getLastMessageId() == null ? null : last.get(t.getLastMessageId());
            Long mine = viewer.role() == SenderRole.CUSTOMER ? t.getCustomerLastReadMessageId() : t.getOwnerLastReadMessageId();
            Long theirs = viewer.role() == SenderRole.CUSTOMER ? t.getOwnerLastReadMessageId() : t.getCustomerLastReadMessageId();
            out.add(
                    new ChatViews.Thread(
                            String.valueOf(t.getId()),
                            new ChatViews.PlaceRef(t.getPlaceId(), ChatPlaceLookup.text(p, "name"), ChatPlaceLookup.text(p, "thumbnailUrl")),
                            t.getProgramId() == null ? null : new ChatViews.ProgramRef(t.getProgramId(), t.getProgramTitle()),
                            new ChatViews.CustomerRef(names.get(t.getCustomerMemberId())),
                            t.getStatus(),
                            lm == null ? null : lastMessage(lm),
                            unread.getOrDefault(t.getId(), 0L),
                            unanswered(t),
                            str(mine),
                            str(theirs),
                            ApiTimestamps.format(t.getCreatedAt())));
        }
        return out;
    }

    /**
     * 미답변(C0, C9 보강): OPEN 이고 마지막 메시지의 보낸 쪽이 CUSTOMER. 마지막 메시지가 <b>삭제됐어도</b> 보낸 쪽 판정에 그대로
     * 포함한다 — 고객이 보냈다 지운 문의도 오너가 확인할 대상이다(답장하면 0 이 된다).
     */
    public static boolean unanswered(CenterChatThreadJpaEntity t) {
        return ThreadStatus.OPEN.name().equals(t.getStatus()) && SenderRole.CUSTOMER.name().equals(t.getLastSenderRole());
    }

    static ChatViews.LastMessage lastMessage(CenterChatMessageJpaEntity m) {
        if (m.isDeleted()) {
            return new ChatViews.LastMessage(String.valueOf(m.getId()), m.getSenderRole(), null, 0, true, ApiTimestamps.format(m.getCreatedAt()));
        }
        return new ChatViews.LastMessage(
                String.valueOf(m.getId()), m.getSenderRole(), preview(m.getBody(), PREVIEW), m.imageCount(), false, ApiTimestamps.format(m.getCreatedAt()));
    }

    public ChatViews.Message message(Viewer viewer, CenterChatMessageJpaEntity m) {
        boolean mine = viewer.role().name().equals(m.getSenderRole());
        boolean deleted = m.isDeleted();
        return new ChatViews.Message(
                String.valueOf(m.getId()),
                String.valueOf(m.getThreadId()),
                m.getSenderRole(),
                mine,
                deleted ? null : m.getBody(),
                deleted ? List.of() : images(viewer.basePath(), m),
                mine ? m.getClientMessageId() : null,
                deleted,
                deletable(viewer, m),
                ApiTimestamps.format(m.getCreatedAt()));
    }

    /** C15 — ChatMessageService.delete 의 허용 조건(같은 보낸 쪽 + 같은 회원)과 같다. 삭제된 메시지는 false. */
    static boolean deletable(Viewer viewer, CenterChatMessageJpaEntity m) {
        return !m.isDeleted()
                && viewer.role().name().equals(m.getSenderRole())
                && viewer.memberId() != null
                && viewer.memberId().equals(m.getSenderMemberId());
    }

    public List<ChatViews.Message> messages(Viewer viewer, Collection<CenterChatMessageJpaEntity> list) {
        return list.stream().map(m -> message(viewer, m)).toList();
    }

    public List<ChatViews.AdminMessage> adminMessages(List<CenterChatMessageJpaEntity> list) {
        Map<String, MemberDirectory.MemberBrief> briefs = members.findBriefs(list.stream().map(CenterChatMessageJpaEntity::getSenderMemberId).toList());
        return list.stream()
                .map(m -> {
                    MemberDirectory.MemberBrief b = briefs.get(m.getSenderMemberId());
                    return new ChatViews.AdminMessage(
                            String.valueOf(m.getId()),
                            String.valueOf(m.getThreadId()),
                            m.getSenderRole(),
                            new ChatViews.MemberEmail(m.getSenderMemberId(), b == null ? null : b.email()),
                            m.getBody(),
                            images("/admin/center-chats", m),
                            m.isDeleted(),
                            ApiTimestamps.format(m.getDeletedAt()),
                            ApiTimestamps.format(m.getCreatedAt()));
                })
                .toList();
    }

    static List<ChatViews.Image> images(String base, CenterChatMessageJpaEntity m) {
        List<ChatViews.Image> out = new ArrayList<>();
        JsonNode imgs = m.getImages();
        if (imgs == null || !imgs.isArray()) {
            return out;
        }
        for (int i = 0; i < imgs.size(); i++) {
            JsonNode img = imgs.get(i);
            out.add(
                    new ChatViews.Image(
                            base + "/" + m.getThreadId() + "/messages/" + m.getId() + "/images/" + i,
                            img.path("contentType").asText(),
                            img.path("sizeBytes").asLong()));
        }
        return out;
    }

    public static String preview(String body, int max) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String flat = body.replaceAll("\\s*[\\r\\n]+\\s*", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max);
    }

    static String str(Long v) {
        return v == null ? null : String.valueOf(v);
    }
}
