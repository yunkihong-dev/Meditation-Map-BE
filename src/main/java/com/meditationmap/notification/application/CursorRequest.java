package com.meditationmap.notification.application;

import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 커서 쿼리 해석 (계약 0절): {@code size} 기본 20·최대 50(초과는 50으로 자름), {@code cursor} 는 숫자 id.
 * 숫자가 아니면 400 INVALID_PARAMETER.
 *
 * <p>다음 페이지 유무를 알려고 size+1 건을 읽는다.
 */
public record CursorRequest(Long cursor, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public static CursorRequest parse(String cursorRaw, Integer sizeRaw) {
        Long cursor = null;
        if (cursorRaw != null && !cursorRaw.isBlank()) {
            try {
                cursor = Long.parseLong(cursorRaw.trim());
            } catch (NumberFormatException e) {
                throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
            }
            if (cursor <= 0) {
                throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
            }
        }
        int size = sizeRaw == null ? DEFAULT_SIZE : sizeRaw;
        if (size < 1) {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
        return new CursorRequest(cursor, Math.min(size, MAX_SIZE));
    }

    public Pageable probe() {
        return PageRequest.of(0, size + 1);
    }
}
