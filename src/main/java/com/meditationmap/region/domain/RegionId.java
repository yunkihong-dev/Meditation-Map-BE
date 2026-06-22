package com.meditationmap.region.domain;

import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.Objects;

public record RegionId(String value) {

    public RegionId {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new DomainArgumentException(ErrorCode.INVALID_REGION_ID);
        }
    }

    public static RegionId of(String raw) {
        return new RegionId(raw.trim());
    }

    public boolean isAllScope() {
        return "all".equalsIgnoreCase(value);
    }
}
