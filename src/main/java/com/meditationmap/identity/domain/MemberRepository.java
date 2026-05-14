package com.meditationmap.identity.domain;

import java.util.Optional;

public interface MemberRepository {

    void save(Member member);

    Optional<Member> findByEmail(Email email);

    Optional<Member> findByOauth(String provider, String subject);

    boolean existsByEmail(Email email);
}
