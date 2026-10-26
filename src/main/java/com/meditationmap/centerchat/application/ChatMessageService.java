package com.meditationmap.centerchat.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.meditationmap.centerchat.application.ChatAccess.Viewer;
import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.centerchat.domain.ChatEnums.ThreadStatus;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatMessageJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatMessageSpringDataRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadSpringDataRepository;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.notification.application.NotificationReadService;
import com.meditationmap.notification.domain.NotificationRefType;
import com.meditationmap.notification.domain.NotificationType;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import com.meditationmap.storage.application.port.out.ObjectStoragePort;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 전송·목록·읽음·삭제·사진(채팅 C3.5~C3.8, C9 소프트 삭제). */
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    public static final int MAX_BODY = 2000;
    public static final int MAX_IMAGES = 3;
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 25L * 1024 * 1024;
    static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final ChatAccess access;
    private final CenterChatThreadSpringDataRepository threads;
    private final CenterChatMessageSpringDataRepository messages;
    private final ChatPlaceLookup places;
    private final PlaceOwnership ownership;
    private final MemberDirectory members;
    private final ChatAssembler assembler;
    private final ChatNotifier notifier;
    private final NotificationReadService notificationReads;
    private final ObjectProvider<ObjectStoragePort> storage;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public record ImageInput(String originalFilename, String contentType, long size, byte[] bytes) {}

    public record SendResult(boolean created, ChatViews.Message message) {}

    /**
     * 전송. 대화 행 FOR UPDATE 로 직렬화하고 READ COMMITTED 라 같은 clientMessageId 로 줄 선 두 번째 요청은 첫 요청이 커밋한 행을
     * 보고 200 + 그 메시지를 돌려준다(새로 만들지 않고 알림·SSE 도 다시 보내지 않는다).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SendResult send(Viewer viewer, String threadIdRaw, String clientMessageId, String bodyRaw, List<ImageInput> images) {
        String body = bodyRaw == null ? null : bodyRaw.strip();
        if (body != null && body.isEmpty()) {
            body = null;
        }
        List<ImageInput> imgs = images == null ? List.of() : images.stream().filter(i -> i != null && i.size() > 0).toList();
        Map<String, String> errors = new LinkedHashMap<>();
        if (clientMessageId == null || !UUID.matcher(clientMessageId.trim()).matches()) {
            errors.put("clientMessageId", "UUID 여야 합니다.");
        }
        if (body == null && imgs.isEmpty()) {
            errors.put("body", "본문이나 사진 중 하나는 있어야 합니다.");
        }
        if (body != null && body.length() > MAX_BODY) {
            errors.put("body", "2000자 이하여야 합니다.");
        }
        if (imgs.size() > MAX_IMAGES) {
            errors.put("images", "사진은 최대 3장입니다.");
        }
        CenterChatThreadJpaEntity t = access.lock(viewer, threadIdRaw);
        if (!errors.isEmpty()) {
            throw DetailedDomainException.fields(ErrorCode.VALIDATION_FAILED, errors);
        }
        String cmid = clientMessageId.trim().toLowerCase(Locale.ROOT);
        Optional<CenterChatMessageJpaEntity> dup =
                messages.findByThreadIdAndSenderRoleAndClientMessageId(t.getId(), viewer.role().name(), cmid);
        if (dup.isPresent()) {
            return new SendResult(false, assembler.message(viewer, dup.get()));
        }
        long total = 0;
        for (ImageInput i : imgs) {
            if (!IMAGE_TYPES.contains(normalizeType(i.contentType()))) {
                throw new DomainArgumentException(ErrorCode.CENTER_CHAT_IMAGE_TYPE_NOT_ALLOWED);
            }
            if (i.size() > MAX_IMAGE_BYTES) {
                throw new DomainArgumentException(ErrorCode.UPLOAD_TOO_LARGE);
            }
            total += i.size();
        }
        if (total > MAX_TOTAL_BYTES) {
            throw new DomainArgumentException(ErrorCode.UPLOAD_TOO_LARGE);
        }
        if (ThreadStatus.BLOCKED.name().equals(t.getStatus())) {
            throw new DomainArgumentException(ErrorCode.CENTER_CHAT_BLOCKED);
        }
        PlaceJpaEntity place = places.find(t.getPlaceId()).orElse(null);
        if (place == null) {
            throw new DetailedDomainException(ErrorCode.CENTER_CHAT_UNAVAILABLE, Map.of("reason", "PLACE_DELETED"));
        }
        Optional<String> owner = ownership.ownerOf(t.getPlaceId());
        if (owner.isEmpty()) {
            throw new DetailedDomainException(ErrorCode.CENTER_CHAT_UNAVAILABLE, Map.of("reason", "NO_OWNER"));
        }

        ArrayNode stored = objectMapper.createArrayNode();
        if (!imgs.isEmpty()) {
            ObjectStoragePort port = storage.getIfAvailable();
            if (port == null) {
                throw new DomainArgumentException(ErrorCode.FILE_STORAGE_DISABLED);
            }
            for (ImageInput i : imgs) {
                String type = normalizeType(i.contentType());
                try {
                    ObjectStoragePort.StoredObject o = port.putPrivate("center-chat", i.originalFilename(), type, i.bytes());
                    stored.addObject().put("key", o.objectKey()).put("contentType", type).put("sizeBytes", i.size());
                } catch (RuntimeException e) {
                    throw new InfrastructureException(ErrorCode.FILE_STORE_FAILED, e);
                }
            }
        }
        Instant now = clock.instant();
        CenterChatMessageJpaEntity m = new CenterChatMessageJpaEntity();
        m.setThreadId(t.getId());
        m.setSenderRole(viewer.role().name());
        m.setSenderMemberId(viewer.memberId());
        m.setBody(body);
        m.setImages(stored.isEmpty() ? null : stored);
        m.setClientMessageId(cmid);
        m.setCreatedAt(now);
        m = messages.saveAndFlush(m);

        t.setLastMessageId(m.getId());
        t.setLastMessageAt(now);
        t.setLastSenderRole(viewer.role().name());
        threads.saveAndFlush(t);

        String recipient = viewer.role() == SenderRole.CUSTOMER ? owner.get() : t.getCustomerMemberId();
        Long recipientRead = viewer.role() == SenderRole.CUSTOMER ? t.getOwnerLastReadMessageId() : t.getCustomerLastReadMessageId();
        long unread = messages.countFrom(t.getId(), viewer.role().name(), recipientRead);
        String customerName = members.displayNames(List.of(t.getCustomerMemberId())).get(t.getCustomerMemberId());
        notifier.message(
                t.getId(), viewer.role(), recipient, ChatPlaceLookup.text(place, "name"), customerName, body, m.imageCount(), unread);
        events.publishEvent(new ChatEvents.MessageCreated(t.getId(), m.getId()));
        return new SendResult(true, assembler.message(viewer, m));
    }

    @Transactional(readOnly = true)
    public ChatViews.Page<ChatViews.Message> list(Viewer viewer, String threadIdRaw, String cursorRaw, Integer sizeRaw, String afterRaw) {
        CenterChatThreadJpaEntity t = access.load(viewer, threadIdRaw);
        return page(t.getId(), cursorRaw, sizeRaw, afterRaw, ms -> assembler.messages(viewer, ms));
    }

    /** 관리자 열람(C5.4): 원문 + 삭제 여부. 읽음 상태는 바꾸지 않는다. */
    @Transactional(readOnly = true)
    public ChatViews.Page<ChatViews.AdminMessage> adminList(Long threadId, String cursorRaw, Integer sizeRaw) {
        return page(threadId, cursorRaw, sizeRaw, null, assembler::adminMessages);
    }

    private <T> ChatViews.Page<T> page(
            Long threadId, String cursorRaw, Integer sizeRaw, String afterRaw,
            java.util.function.Function<List<CenterChatMessageJpaEntity>, List<T>> map) {
        if (cursorRaw != null && afterRaw != null) {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
        if (afterRaw != null) {
            long after = parsePositive(afterRaw, true);
            List<CenterChatMessageJpaEntity> rows = messages.after(threadId, after, PageRequest.of(0, 101));
            boolean more = rows.size() > 100;
            List<CenterChatMessageJpaEntity> page = rows.stream().limit(100).toList();
            return new ChatViews.Page<>(map.apply(page), more ? String.valueOf(page.get(page.size() - 1).getId()) : null);
        }
        Long cursor = cursorRaw == null || cursorRaw.isBlank() ? null : parsePositive(cursorRaw, false);
        int size = sizeRaw == null ? 30 : sizeRaw;
        if (size < 1) {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
        size = Math.min(size, 50);
        List<CenterChatMessageJpaEntity> rows = messages.pageDesc(threadId, cursor, PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        List<CenterChatMessageJpaEntity> page = rows.stream().limit(size).toList();
        return new ChatViews.Page<>(map.apply(page), more ? String.valueOf(page.get(page.size() - 1).getId()) : null);
    }

    /** 읽음(C3.7). GREATEST — 역행하지 않는다. 마지막 메시지보다 크면 자른다. 대화의 내 미확인 채팅 알림도 읽음. */
    @Transactional
    public ChatViews.ReadResult read(Viewer viewer, String threadIdRaw, String lastReadRaw) {
        CenterChatThreadJpaEntity t = access.load(viewer, threadIdRaw);
        Long requested = null;
        if (lastReadRaw != null) {
            try {
                requested = Long.parseLong(lastReadRaw.trim());
            } catch (NumberFormatException e) {
                throw DetailedDomainException.fields(ErrorCode.VALIDATION_FAILED, Map.of("lastReadMessageId", "숫자여야 합니다."));
            }
        }
        Long last = t.getLastMessageId();
        if (last != null) {
            long target = requested == null ? last : Math.min(requested, last);
            int raised =
                    viewer.role() == SenderRole.CUSTOMER
                            ? threads.raiseCustomerRead(t.getId(), target)
                            : threads.raiseOwnerRead(t.getId(), target);
            if (raised > 0) {
                events.publishEvent(new ChatEvents.ReadRaised(t.getId(), viewer.role().name(), target));
            }
        }
        notificationReads.markReadByRef(
                viewer.memberId(), NotificationType.CENTER_CHAT_MESSAGE.name(), NotificationRefType.CENTER_CHAT.name(), String.valueOf(t.getId()));
        CenterChatThreadJpaEntity fresh = threads.findById(t.getId()).orElseThrow();
        Long mine = viewer.role() == SenderRole.CUSTOMER ? fresh.getCustomerLastReadMessageId() : fresh.getOwnerLastReadMessageId();
        long unread = messages.countFrom(t.getId(), viewer.role().other().name(), mine);
        return new ChatViews.ReadResult(String.valueOf(t.getId()), mine == null ? null : String.valueOf(mine), unread);
    }

    /**
     * 본인 메시지 소프트 삭제(DC5, 계약 변경 C9). 멱등. 행·원문은 남긴다(deleted_at·deleted_by). 상대에게는 SSE
     * chat-message-deleted, 알림은 만들지 않는다. 남의 메시지 403 FORBIDDEN, 없는 메시지 404.
     */
    @Transactional
    public ChatViews.Message delete(Viewer viewer, String threadIdRaw, String messageIdRaw) {
        CenterChatThreadJpaEntity t = access.lock(viewer, threadIdRaw);
        CenterChatMessageJpaEntity m =
                messages.findByIdAndThreadId(ChatAccess.parseId(messageIdRaw), t.getId()).orElseThrow(ChatAccess::notFound);
        if (!viewer.role().name().equals(m.getSenderRole()) || !viewer.memberId().equals(m.getSenderMemberId())) {
            throw new DomainArgumentException(ErrorCode.FORBIDDEN);
        }
        if (!m.isDeleted()) {
            m.setDeletedAt(clock.instant());
            m.setDeletedByMemberId(viewer.memberId());
            messages.saveAndFlush(m);
            events.publishEvent(new ChatEvents.MessageDeleted(t.getId(), m.getId()));
        }
        return assembler.message(viewer, m);
    }

    public record ImageBytes(byte[] bytes, String contentType) {}

    /** 당사자 사진. 삭제된 메시지의 사진은 404(C9). */
    @Transactional(readOnly = true)
    public ImageBytes image(Viewer viewer, String threadIdRaw, String messageIdRaw, String indexRaw) {
        CenterChatThreadJpaEntity t = access.load(viewer, threadIdRaw);
        return loadImage(t.getId(), messageIdRaw, indexRaw, false);
    }

    /** 관리자 사진 — 삭제된 메시지도 원본을 본다(증거 보존). */
    @Transactional(readOnly = true)
    public ImageBytes adminImage(Long threadId, String messageIdRaw, String indexRaw) {
        return loadImage(threadId, messageIdRaw, indexRaw, true);
    }

    private ImageBytes loadImage(Long threadId, String messageIdRaw, String indexRaw, boolean includeDeleted) {
        CenterChatMessageJpaEntity m =
                messages.findByIdAndThreadId(ChatAccess.parseId(messageIdRaw), threadId).orElseThrow(ChatAccess::notFound);
        if (m.isDeleted() && !includeDeleted) {
            throw ChatAccess.notFound();
        }
        int index;
        try {
            index = Integer.parseInt(indexRaw);
        } catch (NumberFormatException e) {
            throw ChatAccess.notFound();
        }
        if (index < 0 || index >= m.imageCount()) {
            throw ChatAccess.notFound();
        }
        ObjectStoragePort port = storage.getIfAvailable();
        if (port == null) {
            throw new DomainArgumentException(ErrorCode.FILE_STORAGE_DISABLED);
        }
        var img = m.getImages().get(index);
        ObjectStoragePort.ObjectContent c;
        try {
            c = port.get(img.path("key").asText());
        } catch (RuntimeException e) {
            throw new InfrastructureException(ErrorCode.EXTERNAL_SERVICE_ERROR, e);
        }
        if (c == null) {
            throw ChatAccess.notFound();
        }
        return new ImageBytes(c.bytes(), img.path("contentType").asText("application/octet-stream"));
    }

    static String normalizeType(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.toLowerCase(Locale.ROOT).trim();
        int semi = t.indexOf(';');
        if (semi >= 0) {
            t = t.substring(0, semi).trim();
        }
        return "image/jpg".equals(t) ? "image/jpeg" : t;
    }

    private static long parsePositive(String raw, boolean allowZero) {
        try {
            long v = Long.parseLong(raw.trim());
            if (v < 0 || (!allowZero && v == 0)) {
                throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
    }
}
