package com.meditationmap.identity.domain;

import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;

public class DuplicateEmailException extends DomainException {

    public DuplicateEmailException() {
        super(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
