package com.meditationmap.placeowner.application;

import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 다른 컨텍스트(placestats)가 "이 회원이 이 장소의 오너인가" 만 묻는 창구. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceOwnership {

    private final PlaceOwnerSpringDataRepository ownerRepository;

    /** 지금 오너 회원 ID(없으면 empty). 채팅은 대화에 오너를 저장하지 않고 요청마다 이것으로 판정한다(DC2). */
    public java.util.Optional<String> ownerOf(String placeId) {
        return placeId == null
                ? java.util.Optional.empty()
                : ownerRepository.findById(placeId).map(o -> o.getOwnerMemberId());
    }

    public java.util.List<String> placeIdsOwnedBy(String memberId) {
        return ownerRepository.findByOwnerMemberIdOrderByApprovedAtDesc(memberId).stream()
                .map(o -> o.getPlaceId())
                .toList();
    }

    public boolean isOwner(String memberId, String placeId) {
        if (memberId == null || placeId == null) {
            return false;
        }
        return ownerRepository.findById(placeId).map(o -> o.getOwnerMemberId().equals(memberId)).orElse(false);
    }
}
