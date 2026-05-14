package com.meditationmap.identity.infrastructure.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberFavoritePlaceSpringDataRepository
        extends JpaRepository<MemberFavoritePlaceJpaEntity, MemberFavoritePlaceId> {

    List<MemberFavoritePlaceJpaEntity> findAllById_MemberIdOrderByCreatedAtAsc(String memberId);

    @Modifying
    @Query("delete from MemberFavoritePlaceJpaEntity f where f.id.memberId = :memberId")
    void deleteAllForMember(@Param("memberId") String memberId);
}
