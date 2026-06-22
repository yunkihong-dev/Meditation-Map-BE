package com.meditationmap.place.domain;

import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.Objects;

public record PlaceId(String value) {

    public PlaceId {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new DomainArgumentException(ErrorCode.INVALID_PLACE_ID);
        }
    }

    public static PlaceId of(String raw) {
        return new PlaceId(raw.trim());
    }
}
