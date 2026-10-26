package com.meditationmap.shared.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 신규 API 의 시각 표기 — ISO-8601 UTC, 밀리초 3자리 고정({@code 2026-10-07T03:12:45.123Z}).
 *
 * <p>Jackson 기본 직렬화는 나노초 자릿수에 따라 길이가 달라지므로(…45Z / …45.123456Z) 계약 0절의 고정 형식을
 * 지키려고 문자열로 만들어 내보낸다. 값이 없으면 null.
 */
public final class ApiTimestamps {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private ApiTimestamps() {}

    public static String format(Instant instant) {
        return instant == null ? null : FORMAT.format(instant);
    }
}
