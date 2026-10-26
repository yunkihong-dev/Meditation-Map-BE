package com.meditationmap.centerchat.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.centerchat.application.InquirySettingsService;
import com.meditationmap.identity.application.MemberDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 오너 문의 방식 설정(C14). 오너 아님 403 PLACE_NOT_OWNER, 없는 장소 404. */
@Tag(name = "Center Chat (owner)")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class OwnerInquirySettingsController {

    private final MemberDirectory memberDirectory;
    private final InquirySettingsService service;

    @Operation(summary = "문의 방식 조회 (FE GET /me/places/{placeId}/inquiry-settings)")
    @GetMapping("/me/places/{placeId}/inquiry-settings")
    public InquirySettingsService.OwnerSettings get(@AuthenticationPrincipal UserDetails user, @PathVariable String placeId) {
        return service.get(memberDirectory.requireMemberId(user.getUsername()), placeId);
    }

    @Operation(summary = "문의 방식 변경 — 전체 교체 (FE PUT /me/places/{placeId}/inquiry-settings)")
    @PutMapping("/me/places/{placeId}/inquiry-settings")
    public InquirySettingsService.OwnerSettings put(
            @AuthenticationPrincipal UserDetails user, @PathVariable String placeId, @RequestBody(required = false) JsonNode body) {
        return service.put(memberDirectory.requireMemberId(user.getUsername()), placeId, body);
    }
}
