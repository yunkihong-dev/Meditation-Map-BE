package com.meditationmap.identity.infrastructure.jpa;

import com.meditationmap.identity.domain.MemberRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberSpringDataRepository extends JpaRepository<MemberJpaEntity, String> {

    Optional<MemberJpaEntity> findByEmail(String email);

    Optional<MemberJpaEntity> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByPhoneE164(String phoneE164);

    Optional<MemberJpaEntity> findByOauthProviderAndOauthSubject(String oauthProvider, String oauthSubject);

    long countByRole(MemberRole role);

    long countByRoleAndCreatedAtBefore(MemberRole role, Instant createdAt);

    @Query(
            value =
                    """
                    SELECT DATE(CONVERT_TZ(created_at, '+00:00', '+09:00')) AS signupDate,
                           COUNT(*) AS signupCount
                    FROM users
                    WHERE role = :role
                      AND created_at >= :start
                      AND created_at < :end
                    GROUP BY signupDate
                    ORDER BY signupDate
                    """,
            nativeQuery = true)
    List<SignupDayRow> countMemberSignupsByDay(
            @Param("role") String role, @Param("start") Instant start, @Param("end") Instant end);

    interface SignupDayRow {
        LocalDate getSignupDate();

        long getSignupCount();
    }
}
