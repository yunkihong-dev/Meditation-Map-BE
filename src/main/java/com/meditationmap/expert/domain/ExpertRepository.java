package com.meditationmap.expert.domain;

import java.util.List;
import java.util.Optional;

public interface ExpertRepository {

    List<Expert> findAll();

    Optional<Expert> findById(ExpertId id);
}
