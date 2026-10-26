package com.meditationmap.notification.application;

import com.meditationmap.notification.domain.LinkUrlPolicy;
import com.meditationmap.notification.infrastructure.jpa.NotificationJpaEntity;
import com.meditationmap.notification.infrastructure.jpa.NotificationSpringDataRepository;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 저장. 항상 DB 에 먼저 쓰고(호출자 트랜잭션에 합류), 전달은 커밋 뒤 {@link NotificationDispatcher} 가 한다.
 * 그래서 롤백된 신청·승인에 대한 알림은 나가지 않는다.
 */
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationSpringDataRepository notificationRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public List<Long> notify(Collection<String> recipientMemberIds, NotificationDraft draft) {
        if (recipientMemberIds == null || recipientMemberIds.isEmpty()) {
            return List.of();
        }
        if (!LinkUrlPolicy.isAllowed(draft.linkUrl())) {
            throw new DomainArgumentException(ErrorCode.INVALID_LINK_URL);
        }
        Instant now = clock.instant();
        List<NotificationJpaEntity> rows = new ArrayList<>();
        for (String recipient : new LinkedHashSet<>(recipientMemberIds)) {
            if (recipient == null) {
                continue;
            }
            NotificationJpaEntity e = new NotificationJpaEntity();
            e.setRecipientMemberId(recipient);
            e.setType(draft.type().name());
            e.setTitle(truncate(draft.title(), 200));
            e.setBody(truncate(draft.body(), 1000));
            e.setLinkUrl(draft.linkUrl());
            e.setRefType(draft.refType() == null ? null : draft.refType().name());
            e.setRefId(draft.refId());
            e.setCreatedAt(now);
            rows.add(e);
        }
        List<Long> ids = notificationRepository.saveAll(rows).stream().map(NotificationJpaEntity::getId).toList();
        events.publishEvent(new NotificationEvents.NotificationsCreated(ids));
        return ids;
    }

    /**
     * 채팅 알림 합치기(채팅 C7.2): 같은 수신자·type·ref 의 <b>미확인</b> 행을 지우고 새 행 1개를 넣는다. 호출자가 대화 행을 잠근
     * 트랜잭션 안에서 부르므로 같은 대화의 미확인 채팅 알림은 수신자당 최대 1개다. 이미 읽은 행은 지우지 않는다(이력).
     */
    @Transactional
    public List<Long> replaceUnread(String recipientMemberId, NotificationDraft draft) {
        notificationRepository.deleteUnreadByRef(
                recipientMemberId,
                draft.type().name(),
                draft.refType() == null ? null : draft.refType().name(),
                draft.refId());
        return notify(List.of(recipientMemberId), draft);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
