package com.meditationmap.identity.infrastructure.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberFavoritePlaceSpringDataRepository
        extends JpaRepository<MemberFavoritePlaceJpaEntity, MemberFavoritePlaceId> {

    List<MemberFavoritePlaceJpaEntity> findAllById_MemberIdOrderByCreatedAtAsc(String memberId);

    /** 엔티티를 영속성 컨텍스트에 올리지 않고 ID 만 본다(뒤이은 벌크 삭제 + 재삽입이 merge→UPDATE 로 꼬이지 않게). */
    @Query("select f.id.placeId from MemberFavoritePlaceJpaEntity f where f.id.memberId = :memberId")
    List<String> findPlaceIdsByMemberId(@Param("memberId") String memberId);

    @Modifying
    @Query("delete from MemberFavoritePlaceJpaEntity f where f.id.memberId = :memberId")
    void deleteAllForMember(@Param("memberId") String memberId);
}
