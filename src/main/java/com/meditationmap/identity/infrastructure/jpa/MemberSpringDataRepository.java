package com.meditationmap.identity.infrastructure.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberSpringDataRepository extends JpaRepository<MemberJpaEntity, String> {

    Optional<MemberJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<MemberJpaEntity> findByOauthProviderAndOauthSubject(String oauthProvider, String oauthSubject);
}
