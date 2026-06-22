package com.meditationmap.admin.config;

import com.meditationmap.identity.application.port.out.PasswordHasher;
import com.meditationmap.identity.domain.Email;
import com.meditationmap.identity.domain.Member;
import com.meditationmap.identity.domain.MemberId;
import com.meditationmap.identity.domain.MemberRepository;
import com.meditationmap.identity.domain.MemberRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 기동 시 스태프 ADMIN 계정(loginId admin / password admin 기본)을 보장합니다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;

    @Value("${app.admin.bootstrap-login-id:admin}")
    private String bootstrapLoginId;

    @Value("${app.admin.bootstrap-password:admin}")
    private String bootstrapPassword;

    @Value("${app.admin.bootstrap-email:admin@internal.local}")
    private String bootstrapEmail;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(bootstrapLoginId) || !StringUtils.hasText(bootstrapPassword)) {
            return;
        }
        String loginId = bootstrapLoginId.trim();
        Email email = Email.of(bootstrapEmail.trim());

        memberRepository
                .findByLoginId(loginId)
                .ifPresentOrElse(
                        this::ensureAdminRole,
                        () ->
                                memberRepository
                                        .findByEmail(email)
                                        .ifPresentOrElse(
                                                existing -> attachLoginId(existing, loginId),
                                                () -> createStaff(loginId, email)));
    }

    private void createStaff(String loginId, Email email) {
        Member staff =
                Member.registerStaff(
                        MemberId.random(),
                        loginId,
                        email,
                        passwordHasher.hash(bootstrapPassword.trim()),
                        MemberRole.ADMIN);
        memberRepository.save(staff);
        log.info("스태프 ADMIN 부트스트랩 계정 생성 loginId={}", loginId);
    }

    private void attachLoginId(Member existing, String loginId) {
        Member upgraded =
                Member.rehydrate(
                        existing.getId(),
                        loginId,
                        existing.getEmail(),
                        passwordHasher.hash(bootstrapPassword.trim()),
                        existing.getOauthProvider(),
                        existing.getOauthSubject(),
                        existing.getPhoneE164Digits(),
                        MemberRole.ADMIN);
        memberRepository.save(upgraded);
        log.info("기존 계정에 loginId 부여 및 ADMIN 승격 loginId={}", loginId);
    }

    private void ensureAdminRole(Member existing) {
        if (existing.getRole() == MemberRole.ADMIN) {
            return;
        }
        Member promoted =
                Member.rehydrate(
                        existing.getId(),
                        existing.getLoginId(),
                        existing.getEmail(),
                        existing.getPasswordHash(),
                        existing.getOauthProvider(),
                        existing.getOauthSubject(),
                        existing.getPhoneE164Digits(),
                        MemberRole.ADMIN);
        memberRepository.save(promoted);
        log.info("스태프 계정 role=ADMIN 으로 승격 loginId={}", existing.getLoginId());
    }
}
