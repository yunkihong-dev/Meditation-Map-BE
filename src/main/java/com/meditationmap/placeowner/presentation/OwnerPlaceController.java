package com.meditationmap.placeowner.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.placeowner.application.OwnerPlaceService;
import com.meditationmap.placestats.application.PlaceStatsQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 오너 장소 (계약 8절). 수정은 {@link OwnerPlaceService#update} 의 @CacheEvict 를 타도록 빈을 직접 부른다. */
@Tag(name = "Owner Places")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class OwnerPlaceController {

    private final MemberDirectory memberDirectory;
    private final OwnerPlaceService ownerPlaceService;
    private final PlaceStatsQueryService statsQueryService;

    @Operation(summary = "오너 대시보드 통계 — range=1D|1W|1M|ALL (FE GET /me/places/{placeId}/stats)")
    @GetMapping("/me/places/{placeId}/stats")
    public PlaceStatsQueryService.PlaceStats stats(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String placeId,
            @RequestParam(value = "range", required = false) String range) {
        ownerPlaceService.requireOwnedPlace(memberDirectory.requireMemberId(user.getUsername()), placeId);
        return statsQueryService.stats(placeId, range);
    }

    @Operation(summary = "내가 오너인 장소 목록 (FE GET /me/places)")
    @GetMapping("/me/places")
    public OwnerPlaceService.Items myPlaces(@AuthenticationPrincipal UserDetails user) {
        return ownerPlaceService.myPlaces(memberDirectory.requireMemberId(user.getUsername()));
    }

    @Operation(summary = "오너 편집용 장소 payload + editableFields (FE GET /me/places/{placeId})")
    @GetMapping("/me/places/{placeId}")
    public OwnerPlaceService.OwnedPlace get(@AuthenticationPrincipal UserDetails user, @PathVariable String placeId) {
        return ownerPlaceService.get(memberDirectory.requireMemberId(user.getUsername()), placeId);
    }

    @Operation(summary = "오너 장소 부분 수정 — 허용 필드만 (FE PATCH /me/places/{placeId})")
    @PatchMapping("/me/places/{placeId}")
    public OwnerPlaceService.OwnedPlace update(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String placeId,
            @RequestBody JsonNode body) {
        return ownerPlaceService.update(memberDirectory.requireMemberId(user.getUsername()), placeId, body);
    }
}
