package com.meditationmap.place.domain;

import com.meditationmap.region.domain.RegionId;
import java.util.Objects;

public class Place {

    private final PlaceId id;
    private final RegionId regionId;
    private final String jsonPayload;

    public Place(PlaceId id, RegionId regionId, String jsonPayload) {
        this.id = Objects.requireNonNull(id);
        this.regionId = Objects.requireNonNull(regionId);
        this.jsonPayload = Objects.requireNonNull(jsonPayload);
    }

    public PlaceId getId() {
        return id;
    }

    public RegionId getRegionId() {
        return regionId;
    }

    public String getJsonPayload() {
        return jsonPayload;
    }
}
