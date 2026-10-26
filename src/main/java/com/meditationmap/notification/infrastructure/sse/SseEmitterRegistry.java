package com.meditationmap.notification.infrastructure.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.notification.application.NotificationView;
import com.meditationmap.notification.application.port.out.RealtimeNotifier;
import com.meditationmap.notification.infrastructure.config.NotificationProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 회원 ID → 열린 SSE 연결 목록. <b>인메모리</b>다 — BE 는 단일 EC2 라서(request.md). 인스턴스를 늘리면 이 클래스를
 * 바꿔야 한다(브로커 필요).
 *
 * <ul>
 *   <li>연결 직후 {@code retry: 5000} → {@code event: connected} 를 보낸다(earlySend 로 버퍼됐다가 초기화 즉시 flush)
 *   <li>25초마다 {@code :hb} — CloudFront origin_read_timeout(60초)보다 짧게
 *   <li>30분 뒤 서버가 정상 종료 → 브라우저가 retry 간격으로 재연결
 *   <li>회원당 최대 5개. 6번째가 열리면 가장 오래된 연결을 닫는다
 *   <li>{@code SseEmitter.send} 는 스레드 안전하지 않다. 하트비트와 알림 전송이 겹칠 수 있어 연결 단위로 잠근다
 * </ul>
 */
@Slf4j
@Component
public class SseEmitterRegistry implements RealtimeNotifier {

    static final long RETRY_MILLIS = 5000L;

    private final Map<String, List<Connection>> connections = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final NotificationProperties.Sse settings;
    private final ObjectMapper objectMapper;

    public SseEmitterRegistry(NotificationProperties properties, ObjectMapper objectMapper) {
        this.settings = properties.sse();
        this.objectMapper = objectMapper;
    }

    /** 새 연결을 열고 첫 두 이벤트를 큐에 넣는다. */
    public SseEmitter open(String memberId, long unreadCount) {
        SseEmitter emitter = new SseEmitter(settings.timeoutMs());
        Connection conn = new Connection(sequence.incrementAndGet(), memberId, emitter);

        emitter.onCompletion(() -> remove(conn));
        emitter.onTimeout(
                () -> {
                    remove(conn);
                    conn.closeQuietly();
                });
        emitter.onError(e -> remove(conn));

        List<Connection> list = connections.computeIfAbsent(memberId, k -> new CopyOnWriteArrayList<>());
        list.add(conn);
        evictOverflow(memberId, list);

        boolean ok =
                conn.send(RawSseEvent.retry(RETRY_MILLIS))
                        && conn.send(RawSseEvent.event(null, "connected", json(Map.of("unreadCount", unreadCount))));
        if (!ok) {
            remove(conn);
        }
        return emitter;
    }

    @Override
    public boolean deliverNotification(String memberId, NotificationView notification) {
        List<Connection> list = connections.get(memberId);
        if (list == null || list.isEmpty()) {
            return false;
        }
        RawSseEvent event = RawSseEvent.event(notification.id(), "notification", json(notification));
        boolean any = false;
        for (Connection c : list) {
            if (c.send(event)) {
                any = true;
            } else {
                remove(c);
            }
        }
        return any;
    }

    @Override
    public void publishEvent(String memberId, String eventName, Object data) {
        List<Connection> list = connections.get(memberId);
        if (list == null || list.isEmpty()) {
            return;
        }
        RawSseEvent event = RawSseEvent.event(null, eventName, json(data));
        for (Connection c : list) {
            if (!c.send(event)) {
                remove(c);
            }
        }
    }

    @Override
    public void publishUnreadCount(String memberId, long unreadCount) {
        List<Connection> list = connections.get(memberId);
        if (list == null || list.isEmpty()) {
            return;
        }
        RawSseEvent event = RawSseEvent.event(null, "unread-count", json(Map.of("unreadCount", unreadCount)));
        for (Connection c : list) {
            if (!c.send(event)) {
                remove(c);
            }
        }
    }

    @Scheduled(
            fixedRateString = "${app.notification.sse.heartbeat-ms:25000}",
            initialDelayString = "${app.notification.sse.heartbeat-ms:25000}")
    public void heartbeat() {
        RawSseEvent hb = RawSseEvent.heartbeat();
        for (List<Connection> list : connections.values()) {
            for (Connection c : list) {
                if (!c.send(hb)) {
                    remove(c);
                }
            }
        }
    }

    public int connectionCount(String memberId) {
        List<Connection> list = connections.get(memberId);
        return list == null ? 0 : list.size();
    }

    private void evictOverflow(String memberId, List<Connection> list) {
        int max = Math.max(1, settings.maxConnectionsPerMember());
        while (list.size() > max) {
            Connection oldest = list.get(0);
            for (Connection c : list) {
                if (c.seq < oldest.seq) {
                    oldest = c;
                }
            }
            remove(oldest);
            oldest.closeQuietly();
            log.debug("[SSE] member={} 연결 상한 {} 초과 — 가장 오래된 연결 종료", memberId, max);
        }
    }

    private void remove(Connection conn) {
        List<Connection> list = connections.get(conn.memberId);
        if (list == null) {
            return;
        }
        list.remove(conn);
        if (list.isEmpty()) {
            connections.remove(conn.memberId, list);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SSE payload serialize failed", e);
        }
    }

    /** 테스트용: 현재 연결 스냅샷 */
    List<Connection> snapshot(String memberId) {
        List<Connection> list = connections.get(memberId);
        return list == null ? List.of() : new ArrayList<>(list);
    }

    static final class Connection {
        final long seq;
        final String memberId;
        final SseEmitter emitter;
        private volatile boolean closed;

        Connection(long seq, String memberId, SseEmitter emitter) {
            this.seq = seq;
            this.memberId = memberId;
            this.emitter = emitter;
        }

        /** @return 전송 성공 여부. 실패하면 연결은 죽은 것으로 본다 */
        boolean send(RawSseEvent event) {
            if (closed) {
                return false;
            }
            synchronized (this) {
                try {
                    emitter.send(event);
                    return true;
                } catch (Exception e) {
                    // IOException(클라이언트 이탈) · IllegalStateException(이미 완료된 emitter)
                    closed = true;
                    log.debug("[SSE] 전송 실패 member={} seq={}: {}", memberId, seq, e.toString());
                    return false;
                }
            }
        }

        void closeQuietly() {
            closed = true;
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 이미 끝난 연결
            }
        }
    }
}
