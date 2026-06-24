package com.meditationmap.identity.domain;

import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class Member {

    private final MemberId id;
    /** 스태프 로그인 아이디(일반 회원은 null) */
    private final String loginId;
    private final Email email;
    private final String passwordHash;
    private final String oauthProvider;
    private final String oauthSubject;
    private final String phoneE164Digits;
    private final MemberRole role;

    private Member(
            MemberId id,
            String loginId,
            Email email,
            String passwordHash,
            String oauthProvider,
            String oauthSubject,
            String phoneE164Digits,
            MemberRole role) {
        this.id = Objects.requireNonNull(id);
        this.loginId = loginId;
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.oauthProvider = oauthProvider;
        this.oauthSubject = oauthSubject;
        this.phoneE164Digits = phoneE164Digits;
        this.role = role != null ? role : MemberRole.MEMBER;
    }

    public static Member register(MemberId id, Email email, String hashedPassword) {
        return new Member(id, null, email, hashedPassword, null, null, null, MemberRole.MEMBER);
    }

    public static Member registerWithVerifiedPhone(
            MemberId id, Email email, String hashedPassword, String phoneE164Digits) {
        Objects.requireNonNull(phoneE164Digits, "verified phone digits required");
        if (phoneE164Digits.isBlank()) {
            throw new DomainArgumentException(ErrorCode.INVALID_PHONE_NUMBER);
        }
        return new Member(id, null, email, hashedPassword, null, null, phoneE164Digits, MemberRole.MEMBER);
    }

    /** 운영·개발·직원 계정 (아이디 로그인) */
    public static Member registerStaff(
            MemberId id, String loginId, Email email, String hashedPassword, MemberRole staffRole) {
        if (!StringUtils.hasText(loginId)) {
            throw new DomainArgumentException(ErrorCode.INVALID_REQUEST_BODY);
        }
        if (staffRole == null || !staffRole.isStaff()) {
            throw new DomainArgumentException(ErrorCode.INVALID_REQUEST_BODY);
        }
        return new Member(id, loginId.trim(), email, hashedPassword, null, null, null, staffRole);
    }

    /** 명상 전문가 계정 (관리자가 대신 생성, 아이디 로그인) */
    public static Member registerExpertAccount(
            MemberId id, String loginId, Email email, String hashedPassword) {
        if (!StringUtils.hasText(loginId)) {
            throw new DomainArgumentException(ErrorCode.INVALID_REQUEST_BODY);
        }
        return new Member(id, loginId.trim(), email, hashedPassword, null, null, null, MemberRole.EXPERT);
    }

    public Member withLinkedOAuth(String provider, String subject) {
        return new Member(id, loginId, email, passwordHash, provider, subject, phoneE164Digits, role);
    }

    public static Member rehydrate(
            MemberId id,
            String loginId,
            Email email,
            String passwordHash,
            String oauthProvider,
            String oauthSubject,
            String phoneE164Digits,
            MemberRole role) {
        return new Member(
                id, loginId, email, passwordHash, oauthProvider, oauthSubject, phoneE164Digits, role);
    }

    /** JWT·UserDetails 주체 — 스태프는 loginId, 일반 회원은 email */
    public String authenticationName() {
        if (StringUtils.hasText(loginId)) {
            return loginId.trim();
        }
        return email.value();
    }

    public MemberId getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public Email getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getOauthProvider() {
        return oauthProvider;
    }

    public String getOauthSubject() {
        return oauthSubject;
    }

    public String getPhoneE164Digits() {
        return phoneE164Digits;
    }

    public MemberRole getRole() {
        return role;
    }

    public boolean isStaff() {
        return role.isStaff();
    }
}
