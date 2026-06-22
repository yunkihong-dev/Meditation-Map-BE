package com.meditationmap.admin.application;

import java.util.Collection;
import lombok.experimental.UtilityClass;

/** Admin 카탈로그 신규 row — 숫자 문자열 ID ("1", "2", …) 자동 부여 */
@UtilityClass
public class AdminNumericIdGenerator {

    public static String nextId(Collection<String> existingIds) {
        long max =
                existingIds.stream()
                        .mapToLong(AdminNumericIdGenerator::parseNumericOrZero)
                        .max()
                        .orElse(0L);
        return Long.toString(max + 1);
    }

    static long parseNumericOrZero(String id) {
        if (id == null || id.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
