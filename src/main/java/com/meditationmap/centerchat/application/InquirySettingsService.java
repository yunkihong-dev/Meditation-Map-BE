package com.meditationmap.centerchat.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.centerchat.domain.InquirySettingsPolicy;
import com.meditationmap.centerchat.domain.InquirySettingsPolicy.KakaoType;
import com.meditationmap.centerchat.domain.InquirySettingsPolicy.Mode;
import com.meditationmap.centerchat.infrastructure.jpa.PlaceInquirySettingJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.PlaceInquirySettingSpringDataRepository;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.shared.domain.ApiTimestamps;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 문의 방식(C14·C16·C17). 오너만 읽고 바꾼다(GET/PUT /me/places/{id}/inquiry-settings). 공개 노출은 chat-availability(C16)로만.
 * 공개 GET /places·/places/{id} 응답과 캐시는 건드리지 않는다(별도 테이블).
 */
@Service
@RequiredArgsConstructor
public class InquirySettingsService {

    private final PlaceInquirySettingSpringDataRepository repository;
    private final ChatPlaceLookup places;
    private final PlaceOwnership ownership;
    private final Clock clock;

    /** 오너 응답. updatedAt 은 저장한 적 없으면 null. CHAT 이어도 보관된 kakaoType·kakaoUrl 을 돌려준다. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OwnerSettings(String placeId, String mode, String kakaoType, String kakaoUrl, String updatedAt) {}

    /** 지금 오너 기준 실효 설정. 설정이 없거나 다른(이전) 오너가 저장한 것이면 CHAT. */
    public record Effective(Mode mode, KakaoType kakaoType, String kakaoUrl) {
        static final Effective DEFAULT = new Effective(Mode.CHAT, null, null);

        public boolean kakao() {
            return mode == Mode.KAKAO;
        }
    }

    @Transactional(readOnly = true)
    public OwnerSettings get(String memberId, String placeId) {
        requireOwner(memberId, placeId);
        return view(placeId, repository.findById(placeId).filter(r -> memberId.equals(r.getOwnerMemberId())));
    }

    @Transactional
    public OwnerSettings put(String memberId, String placeId, JsonNode body) {
        requireOwner(memberId, placeId);
        InquirySettingsPolicy.Settings s = InquirySettingsPolicy.parse(body);
        PlaceInquirySettingJpaEntity row = repository.findById(placeId).orElseGet(() -> {
            PlaceInquirySettingJpaEntity r = new PlaceInquirySettingJpaEntity();
            r.setPlaceId(placeId);
            return r;
        });
        row.setMode(s.mode().name());
        row.setKakaoType(s.kakaoType() == null ? null : s.kakaoType().name());
        row.setKakaoUrl(s.kakaoUrl());
        row.setOwnerMemberId(memberId);
        row.setUpdatedAt(clock.instant());
        return view(placeId, Optional.of(repository.saveAndFlush(row)));
    }

    /** 공개 판정용. currentOwnerId 가 없으면(오너 없음) 기본값. */
    @Transactional(readOnly = true)
    public Effective effective(String placeId, Optional<String> currentOwnerId) {
        if (currentOwnerId.isEmpty()) {
            return Effective.DEFAULT;
        }
        return repository.findById(placeId)
                .filter(r -> currentOwnerId.get().equals(r.getOwnerMemberId()))
                .map(InquirySettingsService::toEffective)
                .orElse(Effective.DEFAULT);
    }

    private static Effective toEffective(PlaceInquirySettingJpaEntity r) {
        Mode mode = Mode.KAKAO.name().equals(r.getMode()) ? Mode.KAKAO : Mode.CHAT;
        KakaoType type = r.getKakaoType() == null ? null : KakaoType.valueOf(r.getKakaoType());
        // 저장 시 검증하지만, 손으로 고친 행 등 형식이 깨진 KAKAO 설정은 CHAT 으로 본다(엉뚱한 링크로 보내지 않게).
        if (mode == Mode.KAKAO && !InquirySettingsPolicy.validUrl(type, r.getKakaoUrl())) {
            return Effective.DEFAULT;
        }
        return new Effective(mode, type, r.getKakaoUrl());
    }

    private void requireOwner(String memberId, String placeId) {
        places.find(placeId).orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!ownership.isOwner(memberId, placeId)) {
            throw new DomainArgumentException(ErrorCode.PLACE_NOT_OWNER);
        }
    }

    private static OwnerSettings view(String placeId, Optional<PlaceInquirySettingJpaEntity> row) {
        return row.map(r -> new OwnerSettings(
                        placeId, r.getMode(), r.getKakaoType(), r.getKakaoUrl(), ApiTimestamps.format(r.getUpdatedAt())))
                .orElseGet(() -> new OwnerSettings(placeId, Mode.CHAT.name(), null, null, null));
    }
}
