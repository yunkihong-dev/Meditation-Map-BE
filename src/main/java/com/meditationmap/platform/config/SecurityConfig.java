package com.meditationmap.platform.config;

import com.meditationmap.identity.infrastructure.security.JwtAuthenticationFilter;
import com.meditationmap.identity.infrastructure.security.NaverAwareOAuth2UserService;
import com.meditationmap.identity.infrastructure.security.OAuth2LoginSuccessHandler;
import jakarta.servlet.DispatcherType;
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
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
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
            "${app.cors.allowed-origins}")
    private String allowedOrigins;

    /** Spring 기본 /login 대신 보낼 프론트 URL ({@code /profile}). */
    @Value("${app.oauth2.frontend-auth-entry-url}")
    private String oauthFrontendAuthEntryUrl;

    /**
     * 소셜 로그인 결과를 처리하는 프론트 페이지. 성공·실패 모두 이곳으로 보낸다.
     *
     * <p>실패를 로그인 진입 화면으로 되돌리면 사용자에게는 "그냥 처음 화면으로 돌아간 것"으로만 보여
     * 무엇이 잘못됐는지 알 수 없다. 콜백 페이지가 error 파라미터를 받아 화면에 표시한다.
     */
    @Value("${app.oauth2.frontend-callback-url}")
    private String oauthFrontendCallbackUrl;

    /**
     * OAuth2(카카오·구글·네이버)와 JWT API 한 체인. OAuth는 세션·콜백이 필요하므로 IF_REQUIRED.
     * JWT 필터는 /oauth2, /login/oauth2 에서 실행하지 않음(콜백 세션 깨지지 않도록).
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(
                        sm ->
                                sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                        // 세션 고정 방어 전략을 돌리지 않는다. 인증을 세션에 두지 않으므로 바꿀 세션 신원이 없다.
                                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                // 인증은 요청마다 JWT 쿠키로만 세운다. 세션(JSESSIONID)에는 SecurityContext 를 저장하지 않는다.
                // 예전에는 SessionManagementFilter 가 JWT 필터가 세운 인증을 HttpSession 에 저장해서, 로그아웃(토큰 쿠키 삭제)
                // 뒤에도 JSESSIONID 하나로 로그인 상태가 유지됐다(AuthSessionLogoutTest).
                // 세션 자체는 OAuth2 인가 요청(state) 저장에만 쓰인다(HttpSessionOAuth2AuthorizationRequestRepository).
                .securityContext(sc -> sc.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                // 미인증 요청을 세션에 저장해 두는 요청 캐시는 쓰지 않는다(성공 핸들러가 SavedRequest 를 쓰지 않음) — 빈 세션 생성 방지.
                .requestCache(rc -> rc.disable())
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                        new SpaUnauthorizedEntryPoint(oauthFrontendAuthEntryUrl)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        auth ->
                                // SSE(SseEmitter) 완료·타임아웃 시점의 ASYNC 재디스패치와 오류 디스패치는 인가하지 않는다.
                                // JwtAuthenticationFilter 는 OncePerRequestFilter 라 재디스패치에서 다시 돌지 않아
                                // 이 디스패치는 익명으로 보인다. 최초 REQUEST 디스패치의 인가는 아래 규칙 그대로다.
                                auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .requestMatchers("/oauth2/**", "/login/oauth2/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        // 지표는 스태프만. 공개해 두면 전체 엔드포인트 목록과 호출·오류 수,
                                        // DB 커넥션 풀 상태, JVM·톰캣 내부까지 누구나 읽을 수 있어
                                        // 공격자에게 그대로 정찰 자료가 된다.
                                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                                        .hasAnyRole("ADMIN", "DEV")
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
                                        // 장소 상세 조회 비콘(비로그인 포함). 중복·봇 억제는 PlaceViewRecorder 가 한다.
                                        .requestMatchers(HttpMethod.POST, "/places/*/views")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/regions", "/regions/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/interests")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/experts", "/experts/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/notices", "/notices/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/banners")
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
                                                        oauthFrontendCallbackUrl))
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