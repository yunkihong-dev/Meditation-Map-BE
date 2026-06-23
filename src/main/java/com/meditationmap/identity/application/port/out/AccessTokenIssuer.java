package com.meditationmap.identity.application.port.out;

import com.meditationmap.identity.domain.Member;

public interface AccessTokenIssuer {

    IssuedToken issueFor(Member member);

    record IssuedToken(String accessToken, String tokenType, String role) {}
}
