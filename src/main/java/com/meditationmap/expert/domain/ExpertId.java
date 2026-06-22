package com.meditationmap.expert.domain;

import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.Objects;

public record ExpertId(String value) {

    public ExpertId {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new DomainArgumentException(ErrorCode.INVALID_EXPERT_ID);
        }
    }

    public static ExpertId of(String raw) {
        return new ExpertId(raw.trim());
    }
}
