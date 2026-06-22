package com.meditationmap.shared.exception;

import com.meditationmap.shared.domain.DomainException;

/** 도메인 식별자·값 형식이 잘못된 경우 등 4xx 에 매핑할 예외 */
public final class DomainArgumentException extends DomainException {

    public DomainArgumentException(ErrorCode errorCode) {
        super(errorCode);
    }
}
