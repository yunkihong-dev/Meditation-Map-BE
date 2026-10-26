package com.meditationmap.notification.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PushTokenSpringDataRepository extends JpaRepository<PushTokenJpaEntity, Long> {

    Optional<PushTokenJpaEntity> findByToken(String token);

    List<PushTokenJpaEntity> findByMemberIdOrderByLastSeenAtDescIdDesc(String memberId);

    List<PushTokenJpaEntity> findByMemberId(String memberId);

    @Transactional
    @Modifying
    @Query("delete from PushTokenJpaEntity t where t.token = :token and t.memberId = :memberId")
    int deleteByTokenAndMemberId(@Param("token") String token, @Param("memberId") String memberId);

    /**
     * 토큰 upsert. 같은 토큰을 동시에 등록해도 유니크 충돌(500) 없이 마지막 요청의 회원으로 맞춰진다.
     */
    @Transactional
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO push_tokens (token, member_id, platform, created_at, last_seen_at)
                    VALUES (:token, :memberId, :platform, :now, :now)
                    ON DUPLICATE KEY UPDATE member_id = VALUES(member_id),
                                            platform = VALUES(platform),
                                            last_seen_at = VALUES(last_seen_at)
                    """,
            nativeQuery = true)
    int upsert(
            @Param("token") String token,
            @Param("memberId") String memberId,
            @Param("platform") String platform,
            @Param("now") java.time.LocalDateTime nowUtc);

    @Transactional
    @Modifying
    @Query("delete from PushTokenJpaEntity t where t.token = :token")
    int deleteByTokenValue(@Param("token") String token);
}
