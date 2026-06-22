package com.meditationmap.identity.infrastructure.jpa;

import com.meditationmap.identity.domain.MemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class MemberJpaEntity {

    @Id
    @Column(name = "id", length = 36, columnDefinition = "CHAR(36)")
    private String id;

    /** 스태프 로그인 아이디 (일반 회원 null) */
    @Column(name = "login_id", unique = true, length = 64)
    private String loginId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "oauth_provider", length = 32)
    private String oauthProvider;

    @Column(name = "oauth_subject")
    private String oauthSubject;

    @Column(name = "phone_e164", unique = true, length = 16)
    private String phoneE164;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
