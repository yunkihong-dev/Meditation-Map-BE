package com.meditationmap.identity.domain;

import java.util.Objects;

public class Member {

    private final MemberId id;
    private final Email email;
    private final String passwordHash;
    private final String oauthProvider;
    private final String oauthSubject;

    public Member(
            MemberId id,
            Email email,
            String passwordHash,
            String oauthProvider,
            String oauthSubject) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.oauthProvider = oauthProvider;
        this.oauthSubject = oauthSubject;
    }

    public static Member register(MemberId id, Email email, String hashedPassword) {
        return new Member(id, email, hashedPassword, null, null);
    }

    public Member withLinkedOAuth(String provider, String subject) {
        return new Member(id, email, passwordHash, provider, subject);
    }

    public MemberId getId() {
        return id;
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
}
