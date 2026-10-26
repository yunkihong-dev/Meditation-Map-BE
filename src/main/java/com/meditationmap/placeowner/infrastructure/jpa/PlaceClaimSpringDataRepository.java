package com.meditationmap.placeowner.infrastructure.jpa;

import com.meditationmap.placeowner.domain.PlaceClaimStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceClaimSpringDataRepository extends JpaRepository<PlaceClaimJpaEntity, Long> {

    /** {@code SELECT … FOR UPDATE} — 같은 신청 동시 승인·반려를 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PlaceClaimJpaEntity c where c.id = :id")
    Optional<PlaceClaimJpaEntity> findByIdForUpdate(@Param("id") Long id);

    /** 엔티티를 영속성 컨텍스트에 올리지 않고 place_id 만 본다(뒤이은 FOR UPDATE 조회가 최신 상태를 읽게). */
    @Query("select c.placeId from PlaceClaimJpaEntity c where c.id = :id")
    Optional<String> findPlaceIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select c from PlaceClaimJpaEntity c where c.placeId = :placeId and c.status = :status"
                    + " and c.id <> :excludeId order by c.id")
    List<PlaceClaimJpaEntity> findOthersForUpdate(
            @Param("placeId") String placeId,
            @Param("status") PlaceClaimStatus status,
            @Param("excludeId") Long excludeId);

    long countByPlaceIdAndStatusAndIdNot(String placeId, PlaceClaimStatus status, Long id);

    Optional<PlaceClaimJpaEntity> findFirstByPlaceIdAndApplicantMemberIdAndStatus(
            String placeId, String applicantMemberId, PlaceClaimStatus status);

    List<PlaceClaimJpaEntity> findByApplicantMemberIdOrderByIdDesc(String applicantMemberId);

    List<PlaceClaimJpaEntity> findAllByOrderByIdDesc(Pageable page);

    List<PlaceClaimJpaEntity> findByIdLessThanOrderByIdDesc(Long cursor, Pageable page);

    List<PlaceClaimJpaEntity> findByStatusOrderByIdDesc(PlaceClaimStatus status, Pageable page);

    List<PlaceClaimJpaEntity> findByStatusAndIdLessThanOrderByIdDesc(
            PlaceClaimStatus status, Long cursor, Pageable page);
}
