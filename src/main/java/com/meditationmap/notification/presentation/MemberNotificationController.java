package com.meditationmap.notification.presentation;

import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.notification.application.CursorPage;
import com.meditationmap.notification.application.MarketingConsentService;
import com.meditationmap.notification.application.NotificationReadService;
import com.meditationmap.notification.application.NotificationView;
import com.meditationmap.notification.presentation.dto.MarketingConsentRequest;
import com.meditationmap.notification.presentation.dto.PushTokenRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 회원 알림 (계약 2절). {@code /me/**} 는 로그인 필수이고 스태프 토큰은 이 경로에서 인증되지 않는다
 * ({@code JwtAuthenticationFilter#isAllowedForRequest}).
 */
@Tag(name = "Notifications")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MemberNotificationController {

    private final MemberDirectory memberDirectory;
    private final NotificationEndpoints endpoints;
    private final MarketingConsentService consentService;

    @Operation(summary = "내 알림 목록 — 커서 (FE GET /me/notifications)")
    @GetMapping("/me/notifications")
    public CursorPage<NotificationView> list(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return endpoints.list(memberId(user), cursor, size);
    }

    @Operation(summary = "안읽은 알림 수 (FE GET /me/notifications/unread-count)")
    @GetMapping("/me/notifications/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserDetails user) {
        return endpoints.unreadCount(memberId(user));
    }

    @Operation(summary = "알림 1건 읽음 — 멱등 (FE POST /me/notifications/{id}/read)")
    @PostMapping("/me/notifications/{id}/read")
    public NotificationReadService.ReadResult read(
            @AuthenticationPrincipal UserDetails user, @PathVariable String id) {
        return endpoints.markRead(memberId(user), id);
    }

    @Operation(summary = "모두 읽음 (FE POST /me/notifications/read-all)")
    @PostMapping("/me/notifications/read-all")
    public NotificationReadService.ReadAllResult readAll(@AuthenticationPrincipal UserDetails user) {
        return endpoints.markAllRead(memberId(user));
    }

    @Operation(summary = "SSE 구독 — text/event-stream (FE EventSource /me/notifications/stream)")
    @GetMapping("/me/notifications/stream")
    public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal UserDetails user) {
        return endpoints.stream(memberId(user));
    }

    @Operation(summary = "FCM 토큰 등록 upsert (FE PUT /me/push-tokens)")
    @PutMapping("/me/push-tokens")
    public ResponseEntity<Void> registerToken(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody PushTokenRequest body) {
        return endpoints.registerToken(memberId(user), body);
    }

    @Operation(summary = "FCM 토큰 해제 — 멱등 (FE DELETE /me/push-tokens)")
    @DeleteMapping("/me/push-tokens")
    public ResponseEntity<Void> unregisterToken(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody PushTokenRequest body) {
        return endpoints.unregisterToken(memberId(user), body);
    }

    @Operation(summary = "광고성 알림 수신 동의 조회 (FE GET /me/marketing-consent)")
    @GetMapping("/me/marketing-consent")
    public MarketingConsentService.ConsentView consent(@AuthenticationPrincipal UserDetails user) {
        return consentService.get(memberId(user));
    }

    @Operation(summary = "광고성 알림 수신 동의 변경 (FE PUT /me/marketing-consent)")
    @PutMapping("/me/marketing-consent")
    public MarketingConsentService.ConsentView updateConsent(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody MarketingConsentRequest body) {
        return consentService.set(memberId(user), body.pushAgreed());
    }

    private String memberId(UserDetails user) {
        return memberDirectory.requireMemberId(user.getUsername());
    }
}
