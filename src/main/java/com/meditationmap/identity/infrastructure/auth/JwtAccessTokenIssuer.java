package com.meditationmap.identity.infrastructure.auth;

import com.meditationmap.identity.application.port.out.AccessTokenIssuer;
import com.meditationmap.identity.domain.Member;
import com.meditationmap.identity.infrastructure.security.JwtService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtService jwtService;

    @Override
    public IssuedToken issueFor(Member member) {
        String token =
                jwtService.createAccessToken(
                        member.getEmail().value(), Map.of("uid", member.getId().value()));
        return new IssuedToken(token, "Bearer");
    }
}
