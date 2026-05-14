package com.meditationmap.identity.domain;

import java.util.Objects;

public record Email(String value) {

    public Email {
        Objects.requireNonNull(value);
        String t = value.trim().toLowerCase();
        if (t.isEmpty() || !t.contains("@")) {
            throw new IllegalArgumentException("invalid email");
        }
        value = t;
    }

    public static Email of(String raw) {
        return new Email(raw);
    }
}
