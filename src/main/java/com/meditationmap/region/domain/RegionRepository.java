package com.meditationmap.region.domain;

import java.util.List;

public interface RegionRepository {

    List<Region> findAllOrderByName();
}
