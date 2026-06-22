package com.meditationmap.identity.domain;

import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.Objects;
import java.util.UUID;

public record MemberId(String value) {

    public MemberId {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new DomainArgumentException(ErrorCode.INVALID_MEMBER_ID);
        }
    }

    public static MemberId of(String id) {
        return new MemberId(id);
    }

    public static MemberId random() {
        return new MemberId(UUID.randomUUID().toString());
    }
}
