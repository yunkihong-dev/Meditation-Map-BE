package com.meditationmap.notification.application;

import com.meditationmap.notification.infrastructure.jpa.NotificationJpaEntity;
import com.meditationmap.notification.infrastructure.jpa.NotificationSpringDataRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 비동기 디스패치. 요청 스레드를 FCM 호출로 붙잡지 않고, 롤백된 알림은 보내지 않는다.
 *
 * <p>전달 실패는 로그만 남긴다 — 알림은 DB 에 있고 FE 가 재연결 때 목록을 다시 맞춘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    static final int BROADCAST_BATCH = 500;

    private final NotificationSpringDataRepository notificationRepository;
    private final NotificationDelivery delivery;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCreated(NotificationEvents.NotificationsCreated event) {
        for (NotificationJpaEntity n : notificationRepository.findAllById(event.notificationIds())) {
            deliverQuietly(n);
        }
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBroadcast(NotificationEvents.BroadcastCreated event) {
        long after = 0L;
        int total = 0;
        while (true) {
            List<NotificationJpaEntity> batch =
                    notificationRepository.findByBroadcastIdAndIdGreaterThanOrderByIdAsc(
                            event.broadcastId(), after, PageRequest.of(0, BROADCAST_BATCH));
            if (batch.isEmpty()) {
                break;
            }
            for (NotificationJpaEntity n : batch) {
                deliverQuietly(n);
            }
            total += batch.size();
            after = batch.get(batch.size() - 1).getId();
        }
        log.info("[NOTIFY] broadcast={} 전달 시도 {}건", event.broadcastId(), total);
    }

    private void deliverQuietly(NotificationJpaEntity n) {
        try {
            delivery.deliver(n.getRecipientMemberId(), NotificationView.from(n));
        } catch (RuntimeException e) {
            log.warn("[NOTIFY] 전달 실패 notification={} recipient={}", n.getId(), n.getRecipientMemberId(), e);
        }
    }
}
