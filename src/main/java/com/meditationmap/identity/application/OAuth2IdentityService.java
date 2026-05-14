package com.meditationmap.identity.application;

import com.meditationmap.identity.application.port.out.AccessTokenIssuer;
import com.meditationmap.identity.domain.Email;
import com.meditationmap.identity.domain.Member;
import com.meditationmap.identity.domain.MemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OAuth2IdentityService {

    private final MemberRepository memberRepository;
    private final AccessTokenIssuer accessTokenIssuer;

    /** 이미 가입·연동된 회원이면 JWT 발급. 신규면 empty (회원가입 플로우로 보냄). */
    public Optional<AccessTokenIssuer.IssuedToken> loginExistingMemberOnly(
            String emailRaw, String registrationId, String oauthSubject) {
        Email email = Email.of(emailRaw);

        Optional<Member> byOauth = memberRepository.findByOauth(registrationId, oauthSubject);
        if (byOauth.isPresent()) {
            return Optional.of(accessTokenIssuer.issueFor(byOauth.get()));
        }

        Optional<Member> byEmail = memberRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            Member linked = byEmail.get().withLinkedOAuth(registrationId, oauthSubject);
            memberRepository.save(linked);
            return Optional.of(accessTokenIssuer.issueFor(linked));
        }

        return Optional.empty();
    }
}
