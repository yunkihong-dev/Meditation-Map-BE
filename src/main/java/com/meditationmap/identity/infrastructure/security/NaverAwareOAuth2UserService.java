package com.meditationmap.identity.infrastructure.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * 네이버 회원조회 API는 {@code { "resultcode", "message", "response": { "id", "email", ... } }} 형태로
 * 식별자가 한 겹 안에 들어 있습니다. {@code response} 를 펼쳐 카카오/Google 핸들러와 동일하게
 * subject·email 을 읽을 수 있게 합니다.
 *
 * <p>주의: 설정의 {@code user-name-attribute} 는 {@code response} 여야 합니다. Spring 의
 * {@link DefaultOAuth2UserService} 가 응답 <b>최상위</b>에서 그 속성을 찾아 없으면 예외를 던지는데,
 * {@code id} 로 두면 이 클래스가 펼치기도 전에 거기서 먼저 실패합니다. 최상위에 확실히 존재하는
 * {@code response} 를 통과 조건으로 쓰고, 펼친 뒤의 식별자는 아래 {@code FLATTENED_NAME_ATTRIBUTE} 로 고정합니다.
 */
@Component
public class NaverAwareOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    /** 펼친 뒤 사용자 식별자로 쓸 속성. 네이버의 회원 고유 id 다. */
    private static final String FLATTENED_NAME_ATTRIBUTE = "id";

    private static final String NAVER = "naver";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(userRequest);
        if (!NAVER.equals(userRequest.getClientRegistration().getRegistrationId())) {
            return user;
        }

        Object resp = user.getAttributes().get("response");
        if (!(resp instanceof Map<?, ?> responseMap)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "invalid_user_info_response",
                            "네이버 응답에 response 객체가 없습니다: " + user.getAttributes().keySet(),
                            null));
        }

        Map<String, Object> flat = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : responseMap.entrySet()) {
            flat.put(String.valueOf(e.getKey()), e.getValue());
        }

        if (!flat.containsKey(FLATTENED_NAME_ATTRIBUTE)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "invalid_user_info_response",
                            "네이버 response 에 id 가 없습니다: " + flat.keySet(),
                            null));
        }

        return new DefaultOAuth2User(user.getAuthorities(), flat, FLATTENED_NAME_ATTRIBUTE);
    }
}
