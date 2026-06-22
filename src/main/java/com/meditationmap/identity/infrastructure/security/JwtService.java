package com.meditationmap.identity.infrastructure.security;

import com.meditationmap.identity.domain.InvalidOAuthSignupTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String createAccessToken(String subject, Map<String, Object> extraClaims) {
        Date now = new Date();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails user) {
        try {
            String subject = extractUsername(token);
            Date exp = parseClaims(token).getExpiration();
            return subject.equals(user.getUsername()) && exp.after(new Date());
        } catch (ExpiredJwtException | MalformedJwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** OAuth 가입 미완료 시 프론트로 넘길 짧은 TTL 토큰 (회원 생성 전 단계). */
    public String createOAuthSignupTicket(
            String email, String registrationId, String oauthSubject, String profilePictureUrl) {
        Date now = new Date();
        long ttlMs = Duration.ofMinutes(30).toMillis();
        Map<String, Object> claims = new HashMap<>();
        claims.put("typ", "oauth_signup_pending");
        claims.put("registrationId", registrationId);
        claims.put("oauthSubject", oauthSubject);
        if (profilePictureUrl != null && !profilePictureUrl.isBlank()) {
            claims.put("profilePicture", profilePictureUrl.trim());
        }
        return Jwts.builder()
                .claims(claims)
                .subject(email.toLowerCase())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    public OAuthSignupTicketClaims parseOAuthSignupTicket(String token) {
        Claims c;
        try {
            c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidOAuthSignupTokenException();
        }
        if (!"oauth_signup_pending".equals(String.valueOf(c.get("typ")))) {
            throw new InvalidOAuthSignupTokenException();
        }
        Object rid = c.get("registrationId");
        Object sub = c.get("oauthSubject");
        String subjectEmail = c.getSubject();
        if (rid == null || sub == null || subjectEmail == null || subjectEmail.isBlank()) {
            throw new InvalidOAuthSignupTokenException();
        }
        Object pic = c.get("profilePicture");
        String profilePictureUrl = null;
        if (pic != null) {
            String s = String.valueOf(pic).trim();
            if (!s.isBlank()) {
                profilePictureUrl = s;
            }
        }
        return new OAuthSignupTicketClaims(
                subjectEmail.toLowerCase(),
                Objects.toString(rid),
                Objects.toString(sub),
                profilePictureUrl);
    }

    public record OAuthSignupTicketClaims(
            String email, String registrationId, String oauthSubject, String profilePictureUrl) {}

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
