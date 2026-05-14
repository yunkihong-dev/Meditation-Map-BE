package com.meditationmap.identity.infrastructure.jpa;

import com.meditationmap.identity.domain.Email;
import com.meditationmap.identity.domain.Member;
import com.meditationmap.identity.domain.MemberId;
import com.meditationmap.identity.domain.MemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberRepositoryAdapter implements MemberRepository {

    private final MemberSpringDataRepository springData;

    @Override
    public void save(Member member) {
        MemberJpaEntity e =
                springData
                        .findById(member.getId().value())
                        .orElseGet(MemberJpaEntity::new);
        e.setId(member.getId().value());
        e.setEmail(member.getEmail().value());
        e.setPasswordHash(member.getPasswordHash());
        e.setOauthProvider(member.getOauthProvider());
        e.setOauthSubject(member.getOauthSubject());
        springData.save(e);
    }

    @Override
    public Optional<Member> findByEmail(Email email) {
        return springData.findByEmail(email.value()).map(this::toDomain);
    }

    @Override
    public Optional<Member> findByOauth(String provider, String subject) {
        return springData
                .findByOauthProviderAndOauthSubject(provider, subject)
                .map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return springData.existsByEmail(email.value());
    }

    private Member toDomain(MemberJpaEntity e) {
        return new Member(
                MemberId.of(e.getId()),
                Email.of(e.getEmail()),
                e.getPasswordHash(),
                e.getOauthProvider(),
                e.getOauthSubject());
    }
}
