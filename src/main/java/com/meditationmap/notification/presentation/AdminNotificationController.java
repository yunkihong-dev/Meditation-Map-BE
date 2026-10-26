package com.meditationmap.notification.presentation;

import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.notification.application.BroadcastService;
import com.meditationmap.notification.application.CursorPage;
import com.meditationmap.notification.application.CursorRequest;
import com.meditationmap.notification.application.NotificationReadService;
import com.meditationmap.notification.application.NotificationView;
import com.meditationmap.notification.presentation.dto.BroadcastRequest;
import com.meditationmap.notification.presentation.dto.PushTokenRequest;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 관리자 알림·광고 발송 (계약 3절). 수신자 = 로그인한 스태프 본인. 관리자 쿠키({@code mm_admin_access_token})로만
 * 인증된다. 광고 발송은 ADMIN 만(D6) — DEV·EMPLOYEE 는 403 FORBIDDEN.
 */
@Tag(name = "Admin Notifications")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','DEV','EMPLOYEE')")
public class AdminNotificationController {

    private final MemberDirectory memberDirectory;
    private final NotificationEndpoints endpoints;
    private final BroadcastService broadcastService;

    @Operation(summary = "관리자 알림 목록 (FE GET /admin/notifications)")
    @GetMapping("/admin/notifications")
    public CursorPage<NotificationView> list(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return endpoints.list(memberId(user), cursor, size);
    }

    @Operation(summary = "관리자 안읽은 알림 수 (FE GET /admin/notifications/unread-count)")
    @GetMapping("/admin/notifications/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserDetails user) {
        return endpoints.unreadCount(memberId(user));
    }

    @Operation(summary = "관리자 알림 1건 읽음 (FE POST /admin/notifications/{id}/read)")
    @PostMapping("/admin/notifications/{id}/read")
    public NotificationReadService.ReadResult read(
            @AuthenticationPrincipal UserDetails user, @PathVariable String id) {
        return endpoints.markRead(memberId(user), id);
    }

    @Operation(summary = "관리자 알림 모두 읽음 (FE POST /admin/notifications/read-all)")
    @PostMapping("/admin/notifications/read-all")
    public NotificationReadService.ReadAllResult readAll(@AuthenticationPrincipal UserDetails user) {
        return endpoints.markAllRead(memberId(user));
    }

    @Operation(summary = "관리자 SSE 구독 (FE EventSource /admin/notifications/stream)")
    @GetMapping("/admin/notifications/stream")
    public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal UserDetails user) {
        return endpoints.stream(memberId(user));
    }

    @Operation(summary = "관리자 FCM 토큰 등록 (FE PUT /admin/push-tokens)")
    @PutMapping("/admin/push-tokens")
    public ResponseEntity<Void> registerToken(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody PushTokenRequest body) {
        return endpoints.registerToken(memberId(user), body);
    }

    @Operation(summary = "관리자 FCM 토큰 해제 (FE DELETE /admin/push-tokens)")
    @DeleteMapping("/admin/push-tokens")
    public ResponseEntity<Void> unregisterToken(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody PushTokenRequest body) {
        return endpoints.unregisterToken(memberId(user), body);
    }

    @Operation(summary = "광고 알림 발송 — ADMIN 만 (FE POST /admin/notifications/broadcasts)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/notifications/broadcasts")
    public ResponseEntity<BroadcastService.BroadcastResult> broadcast(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody BroadcastRequest body) {
        BroadcastService.Target target = BroadcastService.Target.valueOf(body.target());
        List<String> emails = body.emails() == null ? List.of() : body.emails();
        if (target == BroadcastService.Target.EMAILS && emails.isEmpty()) {
            throw DetailedDomainException.fields(
                    ErrorCode.VALIDATION_FAILED, Map.of("emails", "target=EMAILS 이면 1~200개가 필요합니다."));
        }
        String linkUrl = body.linkUrl() == null || body.linkUrl().isBlank() ? null : body.linkUrl().trim();
        BroadcastService.BroadcastResult result =
                broadcastService.send(
                        memberId(user),
                        new BroadcastService.BroadcastCommand(
                                body.requestId().toLowerCase(),
                                body.title(),
                                body.body().trim(),
                                linkUrl,
                                target,
                                emails));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @Operation(summary = "광고 발송 이력 (FE GET /admin/notifications/broadcasts)")
    @GetMapping("/admin/notifications/broadcasts")
    public CursorPage<BroadcastService.BroadcastView> broadcasts(
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer size) {
        return broadcastService.history(CursorRequest.parse(cursor, size));
    }

    private String memberId(UserDetails user) {
        return memberDirectory.requireMemberId(user.getUsername());
    }
}
