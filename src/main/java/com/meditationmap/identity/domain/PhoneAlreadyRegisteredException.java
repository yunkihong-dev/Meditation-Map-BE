package com.meditationmap.identity.domain;

import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;

public class PhoneAlreadyRegisteredException extends DomainException {

    public PhoneAlreadyRegisteredException() {
        super(ErrorCode.PHONE_ALREADY_REGISTERED);
    }
}
