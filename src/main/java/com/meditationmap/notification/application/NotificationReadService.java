package com.meditationmap.notification.application;

import com.meditationmap.notification.application.port.out.RealtimeNotifier;
import com.meditationmap.notification.infrastructure.jpa.NotificationJpaEntity;
import com.meditationmap.notification.infrastructure.jpa.NotificationSpringDataRepository;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 읽음·모두 읽음. 성공하면(커밋 뒤) 같은 회원의 열린 SSE 연결 전부에 {@code unread-count} 를 보낸다(계약 2.3).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationReadService {

    private final NotificationSpringDataRepository notificationRepository;
    private final RealtimeNotifier realtimeNotifier;
    private final Clock clock;

    /**
     * 멱등. 이미 읽었으면 처음 읽은 시각을 유지한다. 내 알림이 아니거나 없으면 404(남의 알림 존재 여부를 드러내지 않음).
     */
    public ReadResult markRead(String recipientMemberId, String notificationIdRaw) {
        Long id = parseId(notificationIdRaw);
        NotificationJpaEntity n =
                notificationRepository
                        .findByIdAndRecipientMemberId(id, recipientMemberId)
                        .orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        if (n.getReadAt() == null) {
            n.setReadAt(clock.instant());
            notificationRepository.saveAndFlush(n);
        }
        long unread = notificationRepository.countByRecipientMemberIdAndReadAtIsNull(recipientMemberId);
        AfterCommit.run(() -> realtimeNotifier.publishUnreadCount(recipientMemberId, unread));
        return new ReadResult(String.valueOf(n.getId()), true, unread);
    }

    public ReadAllResult markAllRead(String recipientMemberId) {
        int updated = notificationRepository.markAllRead(recipientMemberId, clock.instant());
        long unread = notificationRepository.countByRecipientMemberIdAndReadAtIsNull(recipientMemberId);
        AfterCommit.run(() -> realtimeNotifier.publishUnreadCount(recipientMemberId, unread));
        return new ReadAllResult(updated, unread);
    }

    /**
     * 대화 읽음 등 다른 기능이 "이 대상(ref)의 내 미확인 알림" 을 읽음 처리할 때. 바뀐 행이 있으면 커밋 뒤 unread-count 를 보낸다.
     */
    public int markReadByRef(String recipientMemberId, String type, String refType, String refId) {
        int changed = notificationRepository.markReadByRef(recipientMemberId, type, refType, refId, clock.instant());
        if (changed > 0) {
            long unread = notificationRepository.countByRecipientMemberIdAndReadAtIsNull(recipientMemberId);
            AfterCommit.run(() -> realtimeNotifier.publishUnreadCount(recipientMemberId, unread));
        }
        return changed;
    }

    private static Long parseId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            // 숫자가 아닌 id 는 존재할 수 없는 알림이다.
            throw new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    public record ReadResult(String id, boolean read, long unreadCount) {}

    public record ReadAllResult(int updated, long unreadCount) {}
}
