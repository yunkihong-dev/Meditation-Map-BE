package com.meditationmap.shared.exception;

import com.meditationmap.shared.domain.DomainException;
import java.util.Map;

/**
 * 응답 본문에 {@code details} 를 함께 내보내야 하는 도메인 예외.
 *
 * <p>예: 멀티파트·PATCH 본문처럼 Bean Validation 을 쓸 수 없는 입력의 필드 오류
 * ({@code details.fields}). {@code ApiExceptionHandler} 가 {@code { code, message, details }} 로 바꾼다.
 */
public final class DetailedDomainException extends DomainException {

    private final transient Map<String, Object> details;

    public DetailedDomainException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode);
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    /** {@code details.fields} 한 키만 담는 편의 생성자. */
    public static DetailedDomainException fields(ErrorCode errorCode, Object fields) {
        return new DetailedDomainException(errorCode, Map.of("fields", fields));
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
