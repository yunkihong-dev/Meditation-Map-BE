package com.meditationmap.identity.domain;

import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;

public class PhoneVerificationTokenInvalidException extends DomainException {

    public PhoneVerificationTokenInvalidException() {
        super(ErrorCode.PHONE_VERIFY_TOKEN_INVALID);
    }
}
