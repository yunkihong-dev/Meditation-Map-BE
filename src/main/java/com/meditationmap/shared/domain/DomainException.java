package com.meditationmap.shared.domain;

/** 여러 바운디드 컨텍스트에서 공유하는 도메인 예외 베이스 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
