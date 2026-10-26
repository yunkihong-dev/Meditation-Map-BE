package com.meditationmap.placeowner.presentation;

import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.notification.application.CursorPage;
import com.meditationmap.notification.application.CursorRequest;
import com.meditationmap.placeowner.application.PlaceClaimQueryService;
import com.meditationmap.placeowner.application.PlaceClaimReviewService;
import com.meditationmap.placeowner.application.PlaceClaimViews;
import com.meditationmap.placeowner.application.PlaceEvidenceService;
import com.meditationmap.placeowner.presentation.dto.RejectClaimRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 소유 신청 관리 (계약 5절). */
@Tag(name = "Admin Place Claims")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','DEV','EMPLOYEE')")
public class AdminPlaceClaimController {

    private final MemberDirectory memberDirectory;
    private final PlaceClaimQueryService queryService;
    private final PlaceClaimReviewService reviewService;
    private final PlaceEvidenceService evidenceService;

    @Operation(summary = "소유 신청 목록 — status 기본 PENDING (FE GET /admin/place-claims)")
    @GetMapping("/admin/place-claims")
    public CursorPage<PlaceClaimViews.AdminClaimSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return queryService.adminList(status, CursorRequest.parse(cursor, size));
    }

    @Operation(summary = "소유 신청 상세 (FE GET /admin/place-claims/{id})")
    @GetMapping("/admin/place-claims/{id}")
    public PlaceClaimViews.AdminClaimDetail detail(@PathVariable String id) {
        return queryService.adminDetail(id);
    }

    @Operation(summary = "소유 신청 승인 (FE POST /admin/place-claims/{id}/approve)")
    @PostMapping("/admin/place-claims/{id}/approve")
    public PlaceClaimViews.AdminClaimDetail approve(
            @AuthenticationPrincipal UserDetails user, @PathVariable String id) {
        return reviewService.approve(memberDirectory.requireMemberId(user.getUsername()), id);
    }

    @Operation(summary = "소유 신청 반려 (FE POST /admin/place-claims/{id}/reject)")
    @PostMapping("/admin/place-claims/{id}/reject")
    public PlaceClaimViews.AdminClaimDetail reject(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String id,
            @RequestBody(required = false) RejectClaimRequest body) {
        return reviewService.reject(
                memberDirectory.requireMemberId(user.getUsername()), id, body == null ? null : body.reason());
    }

    @Operation(summary = "소유 신청 증빙 파일 (FE <img>/새 탭 VITE_API_BASE_URL + evidence.url)")
    @GetMapping("/admin/place-claims/{id}/evidence")
    public ResponseEntity<byte[]> evidence(@PathVariable String id) {
        PlaceEvidenceService.EvidenceBytes ev = evidenceService.load(id);
        ContentDisposition disposition =
                (ev.isImage() ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(ev.fileName(), StandardCharsets.UTF_8)
                        .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ev.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(ev.bytes());
    }

    @Operation(summary = "장소 오너 해제 (FE DELETE /admin/place-owners/{placeId})")
    @DeleteMapping("/admin/place-owners/{placeId}")
    public ResponseEntity<Void> releaseOwner(@PathVariable String placeId) {
        reviewService.releaseOwner(placeId);
        return ResponseEntity.noContent().build();
    }
}
