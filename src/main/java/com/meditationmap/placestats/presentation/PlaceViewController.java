package com.meditationmap.placestats.presentation;

import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.identity.infrastructure.security.AuthCookieWriter;
import com.meditationmap.placestats.application.PlaceViewRecorder;
import com.meditationmap.placestats.application.ViewerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조회 비콘. GET /places/{id} 에서 세지 않는 이유: 상세는 @Cacheable 이고 GET 은 프리페치·재시도·여러 컴포넌트의 중복 호출이
 * 생기는 부작용 없는 요청이어야 한다. FE 가 상세 화면을 열 때 한 번 보내고, 중복은 서버가 하루 1회로 거른다.
 * 공개(비로그인 허용). 셌는지 여부는 응답에 드러내지 않는다(항상 204).
 */
@Tag(name = "Place Stats")
@RestController
@RequiredArgsConstructor
public class PlaceViewController {

    private final PlaceViewRecorder recorder;
    private final MemberDirectory memberDirectory;

    @Operation(summary = "장소 상세 조회 기록 — 비콘, 항상 204 (FE POST /places/{id}/views)")
    @PostMapping("/places/{placeId}/views")
    public ResponseEntity<Void> view(
            @PathVariable String placeId, @AuthenticationPrincipal UserDetails user, HttpServletRequest request) {
        String memberId = null;
        if (user != null) {
            try {
                memberId = memberDirectory.requireMemberId(user.getUsername());
            } catch (RuntimeException ignored) {
                memberId = null;
            }
        }
        recorder.record(
                placeId,
                new ViewerContext(memberId, clientIp(request), request.getHeader("User-Agent"), hasStaffCookie(request)));
        return ResponseEntity.noContent().build();
    }

    /** CloudFront 는 X-Forwarded-For 끝에 자기가 본 IP 를 붙인다. 앞쪽 값은 클라이언트가 조작할 수 있어 마지막 값을 쓴다. */
    static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    static boolean hasStaffCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if (AuthCookieWriter.ADMIN_ACCESS_TOKEN_COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }
}
