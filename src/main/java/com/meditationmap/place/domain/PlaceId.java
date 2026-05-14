package com.meditationmap.place.domain;

import java.util.Objects;

public record PlaceId(String value) {

    public PlaceId {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("place id required");
        }
    }

    public static PlaceId of(String raw) {
        return new PlaceId(raw.trim());
    }
}
