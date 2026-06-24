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
        e.setLoginId(member.getLoginId());
        e.setEmail(member.getEmail().value());
        e.setPasswordHash(member.getPasswordHash());
        e.setOauthProvider(member.getOauthProvider());
        e.setOauthSubject(member.getOauthSubject());
        e.setPhoneE164(member.getPhoneE164Digits());
        e.setRole(member.getRole());
        springData.save(e);
    }

    @Override
    public Optional<Member> findByEmail(Email email) {
        return springData.findByEmail(email.value()).map(this::toDomain);
    }

    @Override
    public Optional<Member> findByLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return Optional.empty();
        }
        return springData.findByLoginId(loginId.trim()).map(this::toDomain);
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

    @Override
    public boolean existsByLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return false;
        }
        return springData.existsByLoginId(loginId.trim());
    }

    @Override
    public boolean existsByPhoneE164(String phoneE164Digits) {
        return springData.existsByPhoneE164(phoneE164Digits);
    }

    private Member toDomain(MemberJpaEntity e) {
        return Member.rehydrate(
                MemberId.of(e.getId()),
                e.getLoginId(),
                Email.of(e.getEmail()),
                e.getPasswordHash(),
                e.getOauthProvider(),
                e.getOauthSubject(),
                e.getPhoneE164(),
                e.getRole());
    }
}
