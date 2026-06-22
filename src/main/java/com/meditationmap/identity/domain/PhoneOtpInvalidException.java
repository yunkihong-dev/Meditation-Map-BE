package com.meditationmap.identity.domain;

import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;

public class PhoneOtpInvalidException extends DomainException {

    public PhoneOtpInvalidException() {
        super(ErrorCode.PHONE_OTP_INVALID);
    }
}
