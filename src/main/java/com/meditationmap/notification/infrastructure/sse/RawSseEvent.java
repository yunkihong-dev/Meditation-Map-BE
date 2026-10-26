package com.meditationmap.notification.infrastructure.sse;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 스트림 원문을 계약 2.6 형식 그대로(필드 뒤 공백 포함: {@code event: connected}) 쓰기 위한 이벤트.
 *
 * <p>Spring 기본 빌더는 {@code event:connected} 처럼 공백 없이 쓴다. 브라우저는 둘 다 읽지만 계약이 원문 형식을
 * 고정했으므로 직접 만든다. data 는 호출자가 직렬화한 한 줄 JSON 이어야 한다(개행 금지).
 */
final class RawSseEvent implements SseEmitter.SseEventBuilder {

    static final MediaType TEXT_PLAIN_UTF8 = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    private final String raw;

    private RawSseEvent(String raw) {
        this.raw = raw;
    }

    static RawSseEvent retry(long millis) {
        return new RawSseEvent("retry: " + millis + "\n\n");
    }

    static RawSseEvent event(String id, String name, String jsonLine) {
        StringBuilder sb = new StringBuilder();
        if (id != null) {
            sb.append("id: ").append(id).append('\n');
        }
        sb.append("event: ").append(name).append('\n');
        sb.append("data: ").append(jsonLine.replace("\n", "").replace("\r", "")).append("\n\n");
        return new RawSseEvent(sb.toString());
    }

    static RawSseEvent heartbeat() {
        return new RawSseEvent(":hb\n\n");
    }

    String raw() {
        return raw;
    }

    @Override
    public Set<DataWithMediaType> build() {
        return Set.of(new DataWithMediaType(raw, TEXT_PLAIN_UTF8));
    }

    // 아래 빌더 메서드는 쓰지 않는다 — 원문은 팩터리에서 완성된다.
    @Override
    public SseEmitter.SseEventBuilder id(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SseEmitter.SseEventBuilder name(String eventName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SseEmitter.SseEventBuilder reconnectTime(long reconnectTimeMillis) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SseEmitter.SseEventBuilder comment(String comment) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SseEmitter.SseEventBuilder data(Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SseEmitter.SseEventBuilder data(Object object, MediaType mediaType) {
        throw new UnsupportedOperationException();
    }
}
