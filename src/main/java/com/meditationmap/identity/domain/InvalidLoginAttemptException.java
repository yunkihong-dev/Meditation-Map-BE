package com.meditationmap.identity.domain;

import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;

/** 일반 회원 로그인 시 이메일·비밀번호 불일치 */
public class InvalidLoginAttemptException extends DomainException {

    public InvalidLoginAttemptException() {
        super(ErrorCode.INVALID_CREDENTIALS);
    }
}
