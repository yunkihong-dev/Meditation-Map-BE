package com.meditationmap.centerchat.presentation;

import com.meditationmap.centerchat.application.ChatPlaceLookup;
import com.meditationmap.centerchat.application.ChatViews;
import com.meditationmap.centerchat.application.InquirySettingsService;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 공개 문의 가능 여부(채팅 C2, 문의 방식 C16). 캐시하지 않는다 — 소유 승인·해제·숨김이 즉시 반영돼야 한다. */
@Tag(name = "Center Chat (public)")
@RestController
@RequiredArgsConstructor
public class PlaceChatAvailabilityController {

    private final ChatPlaceLookup places;
    private final PlaceOwnership ownership;
    private final InquirySettingsService inquirySettings;

    @Operation(summary = "문의하기 노출 여부 (FE GET /places/{placeId}/chat-availability)")
    @GetMapping("/places/{placeId}/chat-availability")
    public ChatViews.Availability availability(@PathVariable String placeId) {
        PlaceJpaEntity p = places.find(placeId).orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        Optional<String> owner = ownership.ownerOf(placeId);
        InquirySettingsService.Effective eff = inquirySettings.effective(placeId, owner);
        // 판정 순서: 오너 없음 → 숨김 → 카카오(C16). 앞의 둘은 기존 그대로 우선한다.
        String reason = owner.isEmpty() ? "NO_OWNER" : ChatPlaceLookup.hidden(p) ? "HIDDEN" : eff.kakao() ? "KAKAO" : null;
        boolean kakaoLink = "KAKAO".equals(reason);
        return new ChatViews.Availability(
                placeId,
                reason == null,
                reason,
                eff.mode().name(),
                kakaoLink ? eff.kakaoType().name() : null,
                kakaoLink ? eff.kakaoUrl() : null);
    }
}
