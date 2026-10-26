package com.meditationmap.place.infrastructure.jpa;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceSpringDataRepository extends JpaRepository<PlaceJpaEntity, String> {

    List<PlaceJpaEntity> findByRegionId(String regionId);

    /**
     * {@code SELECT … FOR UPDATE}. 장소 단위 직렬화용 — 소유 신청 승인(같은 장소 동시 승인)과 오너 PATCH(같은 오너 여러 탭)가
     * 이 행 잠금에서 줄을 선다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlaceJpaEntity p where p.id = :id")
    Optional<PlaceJpaEntity> findByIdForUpdate(@Param("id") String id);
}
