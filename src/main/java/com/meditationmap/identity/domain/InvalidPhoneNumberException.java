package com.meditationmap.identity.domain;

import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;

public class InvalidPhoneNumberException extends DomainException {

    public InvalidPhoneNumberException() {
        super(ErrorCode.INVALID_PHONE_NUMBER);
    }
}
