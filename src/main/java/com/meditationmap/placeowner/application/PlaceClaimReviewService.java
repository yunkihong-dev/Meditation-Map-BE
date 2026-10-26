package com.meditationmap.placeowner.application;

import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.placeowner.domain.PlaceClaimStatus;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceBusinessInfoJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceBusinessInfoSpringDataRepository;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimSpringDataRepository;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerSpringDataRepository;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 승인·반려 (계약 5.3·5.4, 계획 3.3).
 *
 * <p>동시성 — 잠금 순서를 "장소 행 → 신청 행(들)" 으로 고정한다. 같은 장소의 서로 다른 신청을 동시에 승인하면 두 트랜잭션이
 * 장소 행에서 줄을 서므로, 신청 행을 서로 엇갈려 잡는 교착이 생기지 않는다. 뒤 트랜잭션은 앞이 커밋한 오너를 보고 409 가 된다.
 * 그래도 PK({@code place_owners.place_id}) 충돌이 나면(잠금을 우회한 경로 등) {@link DataIntegrityViolationException} 을
 * 잡아 409 PLACE_ALREADY_OWNED 로 바꾼다 — 500(DATA_ACCESS_FAILED)으로 새면 계약 위반이다.
 */
@Service
@RequiredArgsConstructor
public class PlaceClaimReviewService {

    private final PlaceClaimSpringDataRepository claimRepository;
    private final PlaceOwnerSpringDataRepository ownerRepository;
    private final PlaceCatalogGateway places;
    private final PlaceClaimNotifier notifier;
    private final PlaceClaimQueryService queryService;
    private final PlaceBusinessInfoSpringDataRepository businessInfoRepository;
    private final Clock clock;

    /**
     * READ COMMITTED — 장소 잠금을 기다린 뒤의 일반 조회(오너 존재 여부)가 앞 트랜잭션이 커밋한 값을 보게 한다. MySQL 기본
     * REPEATABLE READ 면 첫 조회 시점의 스냅샷을 계속 읽어, 이미 생긴 오너를 못 보고 PK 충돌까지 가야 한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlaceClaimViews.AdminClaimDetail approve(String reviewerMemberId, String claimIdRaw) {
        Long claimId = PlaceClaimQueryService.parseClaimId(claimIdRaw);
        String placeId =
                claimRepository
                        .findPlaceIdById(claimId)
                        .orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));

        PlaceJpaEntity place =
                places.lock(placeId).orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        PlaceClaimJpaEntity claim =
                claimRepository
                        .findByIdForUpdate(claimId)
                        .orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        PlaceOwnerJpaEntity existingOwner = ownerRepository.findById(placeId).orElse(null);

        if (claim.getStatus() != PlaceClaimStatus.PENDING) {
            // 다른 신청이 승인되어 자동 반려된 건이면 "이미 오너가 있음" 이 더 정확한 이유다(계약 5.3 오류표).
            if (existingOwner != null && !existingOwner.getClaimId().equals(claim.getId())) {
                throw new DomainArgumentException(ErrorCode.PLACE_ALREADY_OWNED);
            }
            throw new DomainArgumentException(ErrorCode.PLACE_CLAIM_ALREADY_REVIEWED);
        }
        if (existingOwner != null) {
            throw new DomainArgumentException(ErrorCode.PLACE_ALREADY_OWNED);
        }

        Instant now = clock.instant();
        PlaceOwnerJpaEntity owner = new PlaceOwnerJpaEntity();
        owner.setPlaceId(placeId);
        owner.setOwnerMemberId(claim.getApplicantMemberId());
        owner.setClaimId(claim.getId());
        owner.setApprovedByMemberId(reviewerMemberId);
        owner.setApprovedAt(now);
        try {
            ownerRepository.saveAndFlush(owner);
        } catch (DataIntegrityViolationException e) {
            throw new DomainArgumentException(ErrorCode.PLACE_ALREADY_OWNED);
        }

        // 신청서의 사업자번호를 장소의 비공개 사업자 정보로 옮긴다(이미 값이 있으면 덮지 않는다).
        if (claim.getBusinessRegistrationNumber() != null
                && businessInfoRepository.findById(placeId).map(b -> b.getBusinessRegistrationNumber() == null).orElse(true)) {
            PlaceBusinessInfoJpaEntity info = businessInfoRepository.findById(placeId).orElseGet(PlaceBusinessInfoJpaEntity::new);
            info.setPlaceId(placeId);
            info.setBusinessRegistrationNumber(claim.getBusinessRegistrationNumber());
            info.setUpdatedByMemberId(reviewerMemberId);
            info.setUpdatedAt(now);
            businessInfoRepository.save(info);
        }

        claim.setStatus(PlaceClaimStatus.APPROVED);
        claim.setActiveKey(null);
        claim.setReviewedByMemberId(reviewerMemberId);
        claim.setReviewedAt(now);
        claimRepository.save(claim);

        List<PlaceClaimJpaEntity> others =
                claimRepository.findOthersForUpdate(placeId, PlaceClaimStatus.PENDING, claim.getId());
        for (PlaceClaimJpaEntity o : others) {
            o.setStatus(PlaceClaimStatus.REJECTED);
            o.setActiveKey(null);
            o.setRejectReason(PlaceClaimNotifier.AUTO_REJECT_REASON);
            o.setReviewedByMemberId(reviewerMemberId);
            o.setReviewedAt(now);
        }
        claimRepository.saveAll(others);
        claimRepository.flush();

        String placeName = PlaceCatalogGateway.nameOf(place);
        notifier.approved(claim.getId(), claim.getApplicantMemberId(), placeId, placeName);
        for (PlaceClaimJpaEntity o : others) {
            notifier.rejected(o.getId(), o.getApplicantMemberId(), placeName, PlaceClaimNotifier.AUTO_REJECT_REASON);
        }
        return queryService.detail(claim);
    }

    @Transactional
    public PlaceClaimViews.AdminClaimDetail reject(String reviewerMemberId, String claimIdRaw, String reasonRaw) {
        String reason = reasonRaw == null ? null : reasonRaw.trim();
        if (reason == null || reason.isEmpty() || reason.length() > 500) {
            throw DetailedDomainException.fields(ErrorCode.VALIDATION_FAILED, Map.of("reason", "1~500자여야 합니다."));
        }
        Long claimId = PlaceClaimQueryService.parseClaimId(claimIdRaw);
        PlaceClaimJpaEntity claim =
                claimRepository
                        .findByIdForUpdate(claimId)
                        .orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        if (claim.getStatus() != PlaceClaimStatus.PENDING) {
            throw new DomainArgumentException(ErrorCode.PLACE_CLAIM_ALREADY_REVIEWED);
        }
        Instant now = clock.instant();
        claim.setStatus(PlaceClaimStatus.REJECTED);
        claim.setActiveKey(null);
        claim.setRejectReason(reason);
        claim.setReviewedByMemberId(reviewerMemberId);
        claim.setReviewedAt(now);
        claimRepository.saveAndFlush(claim);

        String placeName = PlaceCatalogGateway.nameOf(places.find(claim.getPlaceId()).orElse(null));
        notifier.rejected(claim.getId(), claim.getApplicantMemberId(), placeName, reason);
        return queryService.detail(claim);
    }

    /** 오너 해제(P2). 승인 신청 행은 이력으로 남긴다. 알림은 보내지 않는다. */
    @Transactional
    public void releaseOwner(String placeId) {
        if (!ownerRepository.existsById(placeId)) {
            throw new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        ownerRepository.deleteById(placeId);
    }
}
