package com.meditationmap.expert.domain;

import java.util.Objects;

public record ExpertId(String value) {

    public ExpertId {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("expert id required");
        }
    }

    public static ExpertId of(String raw) {
        return new ExpertId(raw.trim());
    }
}
