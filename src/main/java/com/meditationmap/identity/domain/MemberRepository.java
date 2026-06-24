package com.meditationmap.identity.domain;

import java.util.Optional;

public interface MemberRepository {

    void save(Member member);

    Optional<Member> findByEmail(Email email);

    Optional<Member> findByLoginId(String loginId);

    Optional<Member> findByOauth(String provider, String subject);

    boolean existsByEmail(Email email);

    boolean existsByLoginId(String loginId);

    boolean existsByPhoneE164(String phoneE164Digits);
}
