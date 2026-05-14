package com.meditationmap.identity.infrastructure.security;

import com.meditationmap.identity.application.OAuth2IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2IdentityService oAuth2IdentityService;
    private final JwtService jwtService;

    @Value("${app.oauth2.frontend-callback-url}")
    private String frontendCallbackUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {

        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            response.sendRedirect(withQuery("error", "unsupported"));
            return;
        }

        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        String subject = resolveOauthSubject(oauthToken.getPrincipal(), registrationId);
        if (subject.isEmpty()) {
            response.sendRedirect(withQuery("error", "no_subject"));
            return;
        }

        String emailRaw = resolveEmail(oauthToken.getPrincipal(), registrationId);
        if (emailRaw == null || emailRaw.isBlank()) {
            emailRaw = syntheticEmail(registrationId, subject);
        }

        try {
            String emailNormalized = emailRaw.toLowerCase();
            var existingLogin =
                    oAuth2IdentityService.loginExistingMemberOnly(
                            emailNormalized, registrationId, subject);

            if (existingLogin.isPresent()) {
                var issued = existingLogin.get();
                String url =
                        frontendCallbackUrl
                                + "?accessToken="
                                + URLEncoder.encode(issued.accessToken(), StandardCharsets.UTF_8)
                                + "&tokenType="
                                + URLEncoder.encode(issued.tokenType(), StandardCharsets.UTF_8)
                                + "&email="
                                + URLEncoder.encode(emailNormalized, StandardCharsets.UTF_8);
                getRedirectStrategy().sendRedirect(request, response, url);
                return;
            }

            String phoneDigits =
                    normalizeKrMobileDigits(resolvePhone(oauthToken.getPrincipal(), registrationId));
            String ticket =
                    jwtService.createOAuthSignupTicket(
                            emailNormalized, registrationId, subject);

            String url =
                    frontendCallbackUrl
                            + "?oauthSignupTicket="
                            + URLEncoder.encode(ticket, StandardCharsets.UTF_8)
                            + "&email="
                            + URLEncoder.encode(emailNormalized, StandardCharsets.UTF_8)
                            + "&phone="
                            + URLEncoder.encode(phoneDigits, StandardCharsets.UTF_8);

            getRedirectStrategy().sendRedirect(request, response, url);
        } catch (IllegalArgumentException ex) {
            response.sendRedirect(withQuery("error", "invalid_email"));
        }
    }

    private String withQuery(String key, String value) {
        return frontendCallbackUrl + "?" + key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalizeKrMobileDigits(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String d = raw.replaceAll("\\D", "");
        if (d.startsWith("82") && d.length() >= 11) {
            d = "0" + d.substring(2);
        }
        return d;
    }

    private static String resolveOauthSubject(Object principal, String registrationId) {
        if (principal instanceof OidcUser oidc) {
            return oidc.getSubject() != null ? oidc.getSubject() : "";
        }
        if (principal instanceof OAuth2User user) {
            if ("kakao".equals(registrationId)) {
                Object id = user.getAttribute("id");
                return id != null ? String.valueOf(id) : "";
            }
            Object sub = user.getAttribute("sub");
            if (sub != null) {
                return String.valueOf(sub);
            }
            Object id = user.getAttribute("id");
            return id != null ? String.valueOf(id) : "";
        }
        return "";
    }

    private static String resolveEmail(Object principal, String registrationId) {
        if (principal instanceof OidcUser oidc) {
            return oidc.getEmail();
        }
        if (principal instanceof OAuth2User user) {
            if ("kakao".equals(registrationId)) {
                Object accountObj = user.getAttribute("kakao_account");
                if (accountObj instanceof Map<?, ?> account) {
                    Object em = account.get("email");
                    return em != null ? String.valueOf(em) : null;
                }
                return null;
            }
            Object email = user.getAttribute("email");
            return email != null ? String.valueOf(email) : null;
        }
        return null;
    }

    /** 카카오 동의 시에만 채워짐(`phone_number` 스코프). */
    private static String resolvePhone(Object principal, String registrationId) {
        if (!(principal instanceof OAuth2User user)) {
            return "";
        }
        if (!"kakao".equals(registrationId)) {
            return "";
        }
        Object accountObj = user.getAttribute("kakao_account");
        if (!(accountObj instanceof Map<?, ?> account)) {
            return "";
        }
        Object phone = account.get("phone_number");
        return phone != null ? String.valueOf(phone) : "";
    }

    private static String syntheticEmail(String registrationId, String subject) {
        return registrationId + "-" + subject + "@oauth.meditationmap.local";
    }
}
