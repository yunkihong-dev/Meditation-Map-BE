package com.meditationmap.identity.application;

import com.meditationmap.identity.application.port.out.AccessTokenIssuer;
import com.meditationmap.identity.application.port.out.PasswordHasher;
import com.meditationmap.identity.domain.DuplicateEmailException;
import com.meditationmap.identity.domain.Email;
import com.meditationmap.identity.domain.InvalidOAuthSignupTokenException;
import com.meditationmap.identity.domain.Member;
import com.meditationmap.identity.domain.MemberId;
import com.meditationmap.identity.domain.MemberRepository;
import com.meditationmap.identity.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthApplicationService {

    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final JwtService jwtService;

    public AccessTokenIssuer.IssuedToken register(Email email, String rawPassword) {
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        String hash = passwordHasher.hash(rawPassword);
        Member member = Member.register(MemberId.random(), email, hash);
        memberRepository.save(member);
        return accessTokenIssuer.issueFor(member);
    }

    public AccessTokenIssuer.IssuedToken registerWithOAuth(
            Email email, String rawPassword, String oauthSignupToken) {
        JwtService.OAuthSignupTicketClaims ticket =
                jwtService.parseOAuthSignupTicket(oauthSignupToken);
        if (!ticket.email().equalsIgnoreCase(email.value())) {
            throw new InvalidOAuthSignupTokenException();
        }
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        if (memberRepository.findByOauth(ticket.registrationId(), ticket.oauthSubject()).isPresent()) {
            throw new DuplicateEmailException();
        }
        String hash = passwordHasher.hash(rawPassword);
        Member member =
                Member.register(MemberId.random(), email, hash)
                        .withLinkedOAuth(ticket.registrationId(), ticket.oauthSubject());
        memberRepository.save(member);
        return accessTokenIssuer.issueFor(member);
    }

    @Transactional(readOnly = true)
    public AccessTokenIssuer.IssuedToken login(Email email, String rawPassword) {
        Member member =
                memberRepository
                        .findByEmail(email)
                        .orElseThrow(InvalidCredentialsException::new);
        if (!passwordHasher.matches(rawPassword, member.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return accessTokenIssuer.issueFor(member);
    }

    public static class InvalidCredentialsException extends RuntimeException {}
}
