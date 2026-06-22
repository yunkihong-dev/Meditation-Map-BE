package com.meditationmap.identity.infrastructure.security;

import com.meditationmap.identity.application.OAuth2IdentityService;
import com.meditationmap.shared.exception.DomainArgumentException;
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
    private final AuthCookieWriter authCookieWriter;

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

            String pictureUrl =
                    normalizePictureQueryValue(
                            resolveProfilePictureUrl(oauthToken.getPrincipal(), registrationId));

            if (existingLogin.isPresent()) {
                var issued = existingLogin.get();
                authCookieWriter.writeAccessTokenCookie(response, issued.accessToken());
                clearOAuthSession(request, response);
                StringBuilder sb = new StringBuilder(frontendCallbackUrl);
                sb.append("?login=success");
                if (!pictureUrl.isEmpty()) {
                    sb.append("&picture=").append(URLEncoder.encode(pictureUrl, StandardCharsets.UTF_8));
                }
                getRedirectStrategy().sendRedirect(request, response, sb.toString());
                return;
            }

            String ticket =
                    jwtService.createOAuthSignupTicket(
                            emailNormalized, registrationId, subject, pictureUrl);

            StringBuilder sb = new StringBuilder(frontendCallbackUrl);
            sb.append("?oauthSignupTicket=")
                    .append(URLEncoder.encode(ticket, StandardCharsets.UTF_8))
                    .append("&email=")
                    .append(URLEncoder.encode(emailNormalized, StandardCharsets.UTF_8));
            if (!pictureUrl.isEmpty()) {
                sb.append("&picture=").append(URLEncoder.encode(pictureUrl, StandardCharsets.UTF_8));
            }
            String url = sb.toString();

            clearOAuthSession(request, response);
            getRedirectStrategy().sendRedirect(request, response, url);
        } catch (DomainArgumentException ex) {
            response.sendRedirect(withQuery("error", "invalid_email"));
        }
    }

    private String withQuery(String key, String value) {
        return frontendCallbackUrl + "?" + key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void clearOAuthSession(
            HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.addHeader(
                "Set-Cookie", "JSESSIONID=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
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
            if ("naver".equals(registrationId)) {
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

    private static String normalizePictureQueryValue(String url) {
        if (url == null) return "";
        String t = url.trim();
        if (t.startsWith("http://")) {
            t = "https://" + t.substring("http://".length());
        }
        return t.startsWith("https://") ? t : "";
    }

    private static String resolveProfilePictureUrl(Object principal, String registrationId) {
        if (principal instanceof OidcUser oidc) {
            String p = oidc.getPicture();
            return p != null && !p.isBlank() ? p : "";
        }
        if (principal instanceof OAuth2User user) {
            if ("google".equals(registrationId)) {
                Object pic = user.getAttribute("picture");
                return pic != null ? String.valueOf(pic) : "";
            }
            if ("naver".equals(registrationId)) {
                Object pic = user.getAttribute("profile_image");
                return pic != null && !String.valueOf(pic).isBlank()
                        ? String.valueOf(pic).trim()
                        : "";
            }
            if ("kakao".equals(registrationId)) {
                Object accountObj = user.getAttribute("kakao_account");
                if (!(accountObj instanceof Map<?, ?> account)) {
                    return "";
                }
                Object profileObj = account.get("profile");
                if (!(profileObj instanceof Map<?, ?> profile)) {
                    return "";
                }
                Object full = profile.get("profile_image_url");
                Object thumb = profile.get("thumbnail_image_url");
                if (full != null && !String.valueOf(full).isBlank()) {
                    return String.valueOf(full).trim();
                }
                if (thumb != null && !String.valueOf(thumb).isBlank()) {
                    return String.valueOf(thumb).trim();
                }
            }
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
            if ("naver".equals(registrationId)) {
                Object email = user.getAttribute("email");
                return email != null ? String.valueOf(email) : null;
            }
            Object email = user.getAttribute("email");
            return email != null ? String.valueOf(email) : null;
        }
        return null;
    }

    private static String syntheticEmail(String registrationId, String subject) {
        return registrationId + "-" + subject + "@oauth.meditationmap.local";
    }
}
