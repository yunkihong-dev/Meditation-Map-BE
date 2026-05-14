package com.meditationmap.expert.domain;

import com.meditationmap.region.domain.RegionId;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class Expert {

    private final ExpertId id;
    private final String jsonPayload;
    private final Set<RegionId> serviceRegions;

    public Expert(ExpertId id, String jsonPayload, Set<RegionId> serviceRegions) {
        this.id = Objects.requireNonNull(id);
        this.jsonPayload = Objects.requireNonNull(jsonPayload);
        this.serviceRegions =
                Collections.unmodifiableSet(Set.copyOf(Objects.requireNonNull(serviceRegions)));
    }

    public boolean isListedIn(RegionId queryRegion) {
        if (queryRegion.isAllScope()) {
            return true;
        }
        return serviceRegions.contains(queryRegion);
    }

    public ExpertId getId() {
        return id;
    }

    public String getJsonPayload() {
        return jsonPayload;
    }

    public Set<RegionId> getServiceRegions() {
        return serviceRegions;
    }
}
