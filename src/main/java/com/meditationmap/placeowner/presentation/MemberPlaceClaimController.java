package com.meditationmap.placeowner.presentation;

import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.placeowner.application.ClaimSubmission;
import com.meditationmap.placeowner.application.PlaceClaimQueryService;
import com.meditationmap.placeowner.application.PlaceClaimSubmitService;
import com.meditationmap.placeowner.application.PlaceClaimViews;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 회원 소유 신청 (계약 4절). 스태프 계정은 /me/** 에서 인증되지 않으므로 신청할 수 없다(의도). */
@Tag(name = "Place Claims")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MemberPlaceClaimController {

    private final MemberDirectory memberDirectory;
    private final PlaceClaimSubmitService submitService;
    private final PlaceClaimQueryService queryService;

    @Operation(summary = "장소 소유 신청 — multipart (FE POST /me/place-claims)")
    @PostMapping(value = "/me/place-claims", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlaceClaimViews.MyPlaceClaim> submit(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(value = "placeId", required = false) String placeId,
            @RequestParam(value = "contact", required = false) String contact,
            @RequestParam(value = "memo", required = false) String memo,
            @RequestParam(value = "businessRegistrationNumber", required = false) String brn,
            @RequestPart(value = "evidence", required = false) MultipartFile evidence) {
        ClaimSubmission.EvidenceFile ev = null;
        if (evidence != null && !evidence.isEmpty()) {
            try {
                ev =
                        new ClaimSubmission.EvidenceFile(
                                evidence.getOriginalFilename(),
                                evidence.getContentType(),
                                evidence.getSize(),
                                evidence.getSize() > PlaceClaimSubmitService.MAX_EVIDENCE_BYTES
                                        ? new byte[0]
                                        : evidence.getBytes());
            } catch (IOException e) {
                throw new InfrastructureException(ErrorCode.FILE_STORE_FAILED, e);
            }
        }
        PlaceClaimViews.MyPlaceClaim created =
                submitService.submit(
                        memberDirectory.requireMemberId(user.getUsername()),
                        new ClaimSubmission(placeId, contact, memo, brn, ev));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "내 소유 신청 목록 (FE GET /me/place-claims)")
    @GetMapping("/me/place-claims")
    public PlaceClaimViews.Items<PlaceClaimViews.MyPlaceClaim> mine(@AuthenticationPrincipal UserDetails user) {
        return queryService.mine(memberDirectory.requireMemberId(user.getUsername()));
    }

    @Operation(summary = "내 소유 신청 단건 — 본인 것만, 아니면 404 (FE GET /me/place-claims/{claimId})")
    @GetMapping("/me/place-claims/{claimId}")
    public PlaceClaimViews.MyPlaceClaim mineOne(
            @AuthenticationPrincipal UserDetails user, @org.springframework.web.bind.annotation.PathVariable String claimId) {
        return queryService.mineOne(memberDirectory.requireMemberId(user.getUsername()), claimId);
    }

    @Operation(summary = "장소 상세 버튼용 소유·신청 상태 (FE GET /me/place-claims/status)")
    @GetMapping("/me/place-claims/status")
    public PlaceClaimViews.ClaimStatus status(
            @AuthenticationPrincipal UserDetails user, @RequestParam(value = "placeId", required = false) String placeId) {
        return queryService.status(memberDirectory.requireMemberId(user.getUsername()), placeId);
    }
}
