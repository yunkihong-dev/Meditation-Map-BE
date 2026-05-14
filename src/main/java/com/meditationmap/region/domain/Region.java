package com.meditationmap.region.domain;

import java.util.Objects;

public class Region {

    private final RegionId id;
    private final String name;
    private final String slug;
    private final int sortOrder;

    public Region(RegionId id, String name, String slug, int sortOrder) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.slug = Objects.requireNonNull(slug);
        this.sortOrder = sortOrder;
    }

    public RegionId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
