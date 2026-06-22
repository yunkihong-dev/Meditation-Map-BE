package com.meditationmap.platform.config;

import com.meditationmap.identity.infrastructure.security.JwtAuthenticationFilter;
import com.meditationmap.identity.infrastructure.security.NaverAwareOAuth2UserService;
import com.meditationmap.identity.infrastructure.security.OAuth2LoginSuccessHandler;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** app.cors.allowed-origins 가 비어 있으면(docker .env 빈 값 등) 교차 출처 요청 전부 403 이 나므로 폴백 */
    private static final String DEFAULT_ALLOWED_ORIGINS =
            "http://localhost:3000,http://127.0.0.1:3000";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final NaverAwareOAuth2UserService naverAwareOAuth2UserService;

    @Value(
            "${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOrigins;

    /** Spring 기본 /login 대신 보낼 프론트 URL ({@code /profile}). */
    @Value("${app.oauth2.frontend-auth-entry-url:http://localhost:3000/profile}")
    private String oauthFrontendAuthEntryUrl;

    /**
     * OAuth2(카카오·구글·네이버)와 JWT API 한 체인. OAuth는 세션·콜백이 필요하므로 IF_REQUIRED.
     * JWT 필터는 /oauth2, /login/oauth2 에서 실행하지 않음(콜백 세션 깨지지 않도록).
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                        new SpaUnauthorizedEntryPoint(oauthFrontendAuthEntryUrl)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .requestMatchers("/oauth2/**", "/login/oauth2/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/actuator/info")
                                        .permitAll()
                                        .requestMatchers(
                                                "/swagger-ui.html",
                                                "/swagger-ui/**",
                                                "/v3/api-docs",
                                                "/v3/api-docs/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/auth/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/auth/email/availability")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/places", "/places/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/regions", "/regions/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/experts", "/experts/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/notices", "/notices/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/inquiries")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/admin/auth/login")
                                        .permitAll()
                                        .requestMatchers("/admin/**")
                                        .hasAnyRole("ADMIN", "DEV", "EMPLOYEE")
                                        .requestMatchers(HttpMethod.POST, "/storage/**")
                                        .authenticated()
                                        .anyRequest()
                                        .authenticated())
                .oauth2Login(
                        oauth2 ->
                                oauth2.loginPage(oauthFrontendAuthEntryUrl)
                                        .failureHandler(
                                                new OAuth2LoginFailureRedirectHandler(
                                                        oauthFrontendAuthEntryUrl))
                                        .userInfoEndpoint(
                                                ui ->
                                                        ui.userService(
                                                                naverAwareOAuth2UserService))
                                        .successHandler(oAuth2LoginSuccessHandler))
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> parsed =
                Arrays.stream(allowedOrigins != null ? allowedOrigins.split(",") : new String[] {})
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .filter(s -> !"*".equals(s) && !"**".equals(s))
                        .toList();
        List<String> origins =
                parsed.isEmpty()
                        ? Arrays.stream(DEFAULT_ALLOWED_ORIGINS.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .toList()
                        : parsed;

        // '*' 하나만 허용하려던 값은 브라우저·Spring(Credentials)·스펙과 맞지 않아 필터링됨(parse 비면 폴백).
        boolean hasWildcard = origins.stream().anyMatch(o -> o.contains("*"));
        if (hasWildcard) {
            config.setAllowedOriginPatterns(origins);
        } else {
            config.setAllowedOrigins(origins);
        }

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Collections.singletonList(CorsConfiguration.ALL));
        config.setAllowCredentials(true);
        config.setAllowPrivateNetwork(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
