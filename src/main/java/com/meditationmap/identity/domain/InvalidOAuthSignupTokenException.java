package com.meditationmap.identity.domain;

import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;

public class InvalidOAuthSignupTokenException extends DomainException {

    public InvalidOAuthSignupTokenException() {
        super(ErrorCode.INVALID_OAUTH_SIGNUP_TOKEN);
    }
}
