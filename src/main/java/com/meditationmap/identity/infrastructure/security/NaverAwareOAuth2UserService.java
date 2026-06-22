package com.meditationmap.identity.infrastructure.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * 네이버 회원조회 API는 {@code { "response": { "id", "email", ... } }} 형태라
 * Spring 기본 OAuth2User 속성에 {@code id}가 없습니다. {@code response} 를 펼쳐
 * 카카오/Google 핸들러와 동일한 방식으로 subject·email을 읽을 수 있게 합니다.
 */
@Component
public class NaverAwareOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(userRequest);
        if (!"naver".equals(userRequest.getClientRegistration().getRegistrationId())) {
            return user;
        }
        Object resp = user.getAttributes().get("response");
        if (!(resp instanceof Map<?, ?> responseMap)) {
            return user;
        }
        Map<String, Object> flat = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : responseMap.entrySet()) {
            flat.put(String.valueOf(e.getKey()), e.getValue());
        }
        String nameAttr =
                userRequest
                        .getClientRegistration()
                        .getProviderDetails()
                        .getUserInfoEndpoint()
                        .getUserNameAttributeName();
        if (nameAttr == null || nameAttr.isBlank()) {
            nameAttr = "id";
        }
        return new DefaultOAuth2User(user.getAuthorities(), flat, nameAttr);
    }
}
