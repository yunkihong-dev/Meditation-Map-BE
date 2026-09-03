package com.meditationmap.banner.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.experimental.UtilityClass;

/**
 * 배너가 "지금" 보여야 하는지 판단합니다.
 *
 * 게시 기간은 한국 날짜(YYYY-MM-DD) 기준이고 양끝을 포함합니다.
 * startsAt 이 오늘이면 오늘부터 보이고, endsAt 이 오늘이면 오늘까지 보입니다.
 * 관리자가 급히 내려야 할 때를 위해 enabled 스위치가 기간보다 우선합니다.
 */
@UtilityClass
public class BannerVisibility {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static LocalDate todayKst() {
        return LocalDate.now(KST);
    }

    public static boolean isVisible(JsonNode payload, LocalDate today) {
        if (payload == null || !payload.isObject()) {
            return false;
        }
        // 이미지가 없으면 보여 줄 것이 없습니다.
        if (text(payload, "imageUrl").isEmpty()) {
            return false;
        }
        // enabled 가 없던 예전 데이터는 켜진 것으로 봅니다.
        if (payload.has("enabled") && !payload.get("enabled").asBoolean(true)) {
            return false;
        }

        LocalDate start = date(payload, "startsAt");
        if (start != null && today.isBefore(start)) {
            return false;
        }
        LocalDate end = date(payload, "endsAt");
        return end == null || !today.isAfter(end);
    }

    /** 정렬 순서. 작을수록 앞. 값이 없으면 뒤로 보냅니다. */
    public static int sortOrder(JsonNode payload) {
        if (payload == null || !payload.has("sortOrder")) {
            return Integer.MAX_VALUE;
        }
        return payload.get("sortOrder").asInt(Integer.MAX_VALUE);
    }

    private static String text(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        return node == null || node.isNull() ? "" : node.asText("").trim();
    }

    private static LocalDate date(JsonNode payload, String field) {
        String raw = text(payload, field);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (java.time.format.DateTimeParseException ignored) {
            // 형식이 깨진 값 때문에 배너 전체가 사라지지 않도록 "제한 없음" 으로 봅니다.
            return null;
        }
    }
}
