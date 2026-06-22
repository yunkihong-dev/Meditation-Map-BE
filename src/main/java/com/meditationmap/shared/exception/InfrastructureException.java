package com.meditationmap.shared.exception;

import com.meditationmap.shared.domain.DomainException;

/** 저장소·외부 연동 등 인프라 계층에서의 복구 가능한 장애 */
public final class InfrastructureException extends DomainException {

    public InfrastructureException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public InfrastructureException(ErrorCode errorCode) {
        super(errorCode);
    }
}
