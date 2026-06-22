package com.meditationmap.identity.domain;

import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;

public class PhoneOtpRateLimitedException extends DomainException {

    public PhoneOtpRateLimitedException() {
        super(ErrorCode.PHONE_OTP_SEND_TOO_SOON);
    }
}
