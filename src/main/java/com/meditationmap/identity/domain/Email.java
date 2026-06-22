package com.meditationmap.identity.domain;

import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.Objects;

public record Email(String value) {

    public Email {
        Objects.requireNonNull(value);
        String t = value.trim().toLowerCase();
        if (t.isEmpty() || !t.contains("@")) {
            throw new DomainArgumentException(ErrorCode.INVALID_EMAIL);
        }
        value = t;
    }

    public static Email of(String raw) {
        return new Email(raw);
    }
}
