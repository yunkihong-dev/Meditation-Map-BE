package com.meditationmap.region.domain;

import java.util.Objects;

public record RegionId(String value) {

    public RegionId {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("region id required");
        }
    }

    public static RegionId of(String raw) {
        return new RegionId(raw.trim());
    }

    public boolean isAllScope() {
        return "all".equalsIgnoreCase(value);
    }
}
