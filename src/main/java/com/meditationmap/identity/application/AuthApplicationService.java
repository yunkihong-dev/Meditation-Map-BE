package com.meditationmap.identity.application;

import com.meditationmap.identity.application.port.out.AccessTokenIssuer;
import com.meditationmap.identity.application.port.out.PasswordHasher;
import com.meditationmap.identity.domain.DuplicateEmailException;
import com.meditationmap.identity.domain.Email;
import com.meditationmap.identity.domain.InvalidLoginAttemptException;
import com.meditationmap.identity.domain.InvalidOAuthSignupTokenException;
import com.meditationmap.identity.domain.Member;
import com.meditationmap.identity.domain.MemberId;
import com.meditationmap.identity.domain.MemberRepository;
import com.meditationmap.identity.domain.PhoneAlreadyRegisteredException;
import com.meditationmap.identity.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthApplicationService {

    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final JwtService jwtService;
    private final PhoneSignupVerificationApplicationService phoneSignupVerificationApplicationService;
    private final ProfileImageAttachmentApplicationService profileImageAttachmentApplicationService;
    private final OAuthProfileImageImportApplicationService oAuthProfileImageImportApplicationService;

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(Email email) {
        return !memberRepository.existsByEmail(email);
    }

    /** 이메일·비밀번호 가입: 전화 문자 인증 완료 토큰 필수(Magic number 우회 불가). */
    public AccessTokenIssuer.IssuedToken register(
            Email email, String rawPassword, String phoneVerificationToken, String profileImageObjectKey) {
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        String phoneDigits =
                phoneSignupVerificationApplicationService.consumePhoneVerificationTokenOnce(
                        phoneVerificationToken);

        if (memberRepository.existsByPhoneE164(phoneDigits)) {
            throw new PhoneAlreadyRegisteredException();
        }

        String hash = passwordHasher.hash(rawPassword);
        Member member =
                Member.registerWithVerifiedPhone(MemberId.random(), email, hash, phoneDigits);
        memberRepository.save(member);
        profileImageAttachmentApplicationService.attachPrimaryOnSignup(member.getId(), profileImageObjectKey);
        return accessTokenIssuer.issueFor(member);
    }

    public AccessTokenIssuer.IssuedToken registerWithOAuth(
            Email email, String rawPassword, String oauthSignupToken, String profileImageObjectKey) {
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
        String key = profileImageObjectKey != null ? profileImageObjectKey.trim() : null;
        if (!StringUtils.hasText(key)) {
            key =
                    oAuthProfileImageImportApplicationService.importFromProviderImageUrl(
                            ticket.profilePictureUrl());
        }
        profileImageAttachmentApplicationService.attachPrimaryOnSignup(member.getId(), key);
        return accessTokenIssuer.issueFor(member);
    }

    @Transactional(readOnly = true)
    public AccessTokenIssuer.IssuedToken login(Email email, String rawPassword) {
        Member member =
                memberRepository
                        .findByEmail(email)
                        .orElseThrow(InvalidLoginAttemptException::new);
        if (!passwordHasher.matches(rawPassword, member.getPasswordHash())) {
            throw new InvalidLoginAttemptException();
        }
        return accessTokenIssuer.issueFor(member);
    }

    /** 스태프 전용 — loginId 로그인, ADMIN/DEV/EMPLOYEE 만 허용 */
    @Transactional(readOnly = true)
    public AccessTokenIssuer.IssuedToken staffLogin(String loginId, String rawPassword) {
        if (!StringUtils.hasText(loginId)) {
            throw new InvalidLoginAttemptException();
        }
        Member member =
                memberRepository
                        .findByLoginId(loginId.trim())
                        .orElseThrow(InvalidLoginAttemptException::new);
        if (!member.isStaff()) {
            throw new InvalidLoginAttemptException();
        }
        if (!passwordHasher.matches(rawPassword, member.getPasswordHash())) {
            throw new InvalidLoginAttemptException();
        }
        return accessTokenIssuer.issueFor(member);
    }
}
