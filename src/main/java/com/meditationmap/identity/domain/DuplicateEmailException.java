package com.meditationmap.identity.domain;

import com.meditationmap.shared.domain.DomainException;

public class DuplicateEmailException extends DomainException {

    public DuplicateEmailException() {
        super("이미 사용 중인 이메일입니다.");
    }
}
