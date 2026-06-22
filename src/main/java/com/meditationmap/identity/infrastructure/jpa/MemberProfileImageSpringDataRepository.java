package com.meditationmap.identity.infrastructure.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberProfileImageSpringDataRepository
        extends JpaRepository<MemberProfileImageJpaEntity, String> {

    @Modifying
    @Query(
            "update MemberProfileImageJpaEntity m set m.primaryImage = false where m.memberId = :memberId")
    void clearPrimaryForMember(@Param("memberId") String memberId);

    Optional<MemberProfileImageJpaEntity>
            findFirstByMemberIdAndPrimaryImageTrueOrderBySortOrderAsc(String memberId);
}
