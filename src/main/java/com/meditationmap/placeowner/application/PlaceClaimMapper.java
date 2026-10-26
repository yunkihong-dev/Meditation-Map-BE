package com.meditationmap.placeowner.application;

import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimJpaEntity;
import com.meditationmap.shared.domain.ApiTimestamps;

final class PlaceClaimMapper {

    private PlaceClaimMapper() {}

    static PlaceClaimViews.MyPlaceClaim toMine(PlaceClaimJpaEntity c, String placeName) {
        return new PlaceClaimViews.MyPlaceClaim(
                String.valueOf(c.getId()),
                c.getPlaceId(),
                placeName,
                c.getStatus().name(),
                c.getContact(),
                c.getMemo(),
                c.getBusinessRegistrationNumber(),
                c.hasEvidence(),
                c.hasEvidence() ? c.getEvidenceFileName() : null,
                c.getRejectReason(),
                ApiTimestamps.format(c.getCreatedAt()),
                ApiTimestamps.format(c.getReviewedAt()));
    }

    static String evidenceUrl(Long claimId) {
        return "/admin/place-claims/" + claimId + "/evidence";
    }
}
