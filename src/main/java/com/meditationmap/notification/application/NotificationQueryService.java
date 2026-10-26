package com.meditationmap.notification.application;

import com.meditationmap.notification.infrastructure.jpa.NotificationJpaEntity;
import com.meditationmap.notification.infrastructure.jpa.NotificationSpringDataRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 목록(커서)·안읽은 수. 회원별·실시간이라 캐시하지 않는다(계약 7절). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationSpringDataRepository notificationRepository;

    public CursorPage<NotificationView> page(String recipientMemberId, CursorRequest request) {
        List<NotificationJpaEntity> rows =
                request.cursor() == null
                        ? notificationRepository.findByRecipientMemberIdOrderByIdDesc(
                                recipientMemberId, request.probe())
                        : notificationRepository.findByRecipientMemberIdAndIdLessThanOrderByIdDesc(
                                recipientMemberId, request.cursor(), request.probe());
        boolean hasMore = rows.size() > request.size();
        List<NotificationView> items =
                rows.stream().limit(request.size()).map(NotificationView::from).toList();
        String next = hasMore && !items.isEmpty() ? items.get(items.size() - 1).id() : null;
        return new CursorPage<>(items, next);
    }

    public long unreadCount(String recipientMemberId) {
        return notificationRepository.countByRecipientMemberIdAndReadAtIsNull(recipientMemberId);
    }
}
