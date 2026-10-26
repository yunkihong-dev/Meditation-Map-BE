package com.meditationmap.placeowner.infrastructure.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceOwnerSpringDataRepository extends JpaRepository<PlaceOwnerJpaEntity, String> {

    List<PlaceOwnerJpaEntity> findByOwnerMemberIdOrderByApprovedAtDesc(String ownerMemberId);
}
