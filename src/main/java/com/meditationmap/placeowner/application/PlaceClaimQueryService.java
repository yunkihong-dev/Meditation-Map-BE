package com.meditationmap.placeowner.application;

import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.notification.application.CursorPage;
import com.meditationmap.notification.application.CursorRequest;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.placeowner.domain.PlaceClaimStatus;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimSpringDataRepository;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerSpringDataRepository;
import com.meditationmap.shared.domain.ApiTimestamps;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 신청 목록·상태(4.2·4.3), 관리자 목록·상세(5.1·5.2). 캐시하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceClaimQueryService {

    private final PlaceClaimSpringDataRepository claimRepository;
    private final PlaceOwnerSpringDataRepository ownerRepository;
    private final PlaceCatalogGateway places;
    private final MemberDirectory memberDirectory;

    public PlaceClaimViews.Items<PlaceClaimViews.MyPlaceClaim> mine(String memberId) {
        List<PlaceClaimJpaEntity> rows = claimRepository.findByApplicantMemberIdOrderByIdDesc(memberId);
        Map<String, PlaceJpaEntity> placeMap = places.findAll(rows.stream().map(PlaceClaimJpaEntity::getPlaceId).toList());
        return new PlaceClaimViews.Items<>(
                rows.stream()
                        .map(c -> PlaceClaimMapper.toMine(c, PlaceCatalogGateway.nameOf(placeMap.get(c.getPlaceId()))))
                        .toList());
    }

    /** 내 신청 단건(4.2 원소와 같은 모양). 남의 신청·없는 신청은 404 — 존재 여부를 드러내지 않는다. */
    public PlaceClaimViews.MyPlaceClaim mineOne(String memberId, String claimIdRaw) {
        PlaceClaimJpaEntity c =
                claimRepository
                        .findById(parseClaimId(claimIdRaw))
                        .filter(x -> x.getApplicantMemberId().equals(memberId))
                        .orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        return PlaceClaimMapper.toMine(c, PlaceCatalogGateway.nameOf(places.find(c.getPlaceId()).orElse(null)));
    }

    public PlaceClaimViews.ClaimStatus status(String memberId, String placeIdRaw) {
        String placeId = placeIdRaw == null ? null : placeIdRaw.trim();
        if (placeId == null || placeId.isEmpty()) {
            throw new DomainArgumentException(ErrorCode.MISSING_REQUIRED_PARAMETER);
        }
        places.find(placeId).orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        PlaceOwnerJpaEntity owner = ownerRepository.findById(placeId).orElse(null);
        String pendingId =
                claimRepository
                        .findFirstByPlaceIdAndApplicantMemberIdAndStatus(placeId, memberId, PlaceClaimStatus.PENDING)
                        .map(c -> String.valueOf(c.getId()))
                        .orElse(null);
        boolean hasOwner = owner != null;
        boolean isMine = hasOwner && owner.getOwnerMemberId().equals(memberId);
        return new PlaceClaimViews.ClaimStatus(placeId, hasOwner, isMine, pendingId, !hasOwner && pendingId == null);
    }

    public CursorPage<PlaceClaimViews.AdminClaimSummary> adminList(String statusRaw, CursorRequest request) {
        PlaceClaimStatus status = parseStatusFilter(statusRaw);
        List<PlaceClaimJpaEntity> rows;
        if (status == null) {
            rows =
                    request.cursor() == null
                            ? claimRepository.findAllByOrderByIdDesc(request.probe())
                            : claimRepository.findByIdLessThanOrderByIdDesc(request.cursor(), request.probe());
        } else {
            rows =
                    request.cursor() == null
                            ? claimRepository.findByStatusOrderByIdDesc(status, request.probe())
                            : claimRepository.findByStatusAndIdLessThanOrderByIdDesc(
                                    status, request.cursor(), request.probe());
        }
        boolean hasMore = rows.size() > request.size();
        List<PlaceClaimJpaEntity> page = rows.stream().limit(request.size()).toList();
        Map<String, PlaceJpaEntity> placeMap = places.findAll(page.stream().map(PlaceClaimJpaEntity::getPlaceId).toList());
        Map<String, MemberDirectory.MemberBrief> members =
                memberDirectory.findBriefs(page.stream().map(PlaceClaimJpaEntity::getApplicantMemberId).toList());
        List<PlaceClaimViews.AdminClaimSummary> items =
                page.stream()
                        .map(c -> new PlaceClaimViews.AdminClaimSummary(
                                String.valueOf(c.getId()),
                                c.getPlaceId(),
                                PlaceCatalogGateway.nameOf(placeMap.get(c.getPlaceId())),
                                applicant(c.getApplicantMemberId(), members),
                                c.getStatus().name(),
                                c.hasEvidence(),
                                ApiTimestamps.format(c.getCreatedAt()),
                                ApiTimestamps.format(c.getReviewedAt())))
                        .toList();
        String next = hasMore && !items.isEmpty() ? items.get(items.size() - 1).id() : null;
        return new CursorPage<>(items, next);
    }

    public PlaceClaimViews.AdminClaimDetail adminDetail(String claimIdRaw) {
        PlaceClaimJpaEntity c =
                claimRepository
                        .findById(parseClaimId(claimIdRaw))
                        .orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        return detail(c);
    }

    PlaceClaimViews.AdminClaimDetail detail(PlaceClaimJpaEntity c) {
        String placeName = PlaceCatalogGateway.nameOf(places.find(c.getPlaceId()).orElse(null));
        PlaceOwnerJpaEntity owner = ownerRepository.findById(c.getPlaceId()).orElse(null);
        Map<String, MemberDirectory.MemberBrief> members =
                memberDirectory.findBriefs(
                        java.util.Arrays.asList(
                                c.getApplicantMemberId(),
                                c.getReviewedByMemberId(),
                                owner == null ? null : owner.getOwnerMemberId()));
        PlaceClaimViews.Evidence evidence =
                c.hasEvidence()
                        ? new PlaceClaimViews.Evidence(
                                c.getEvidenceFileName(),
                                c.getEvidenceContentType(),
                                c.getEvidenceSizeBytes(),
                                PlaceClaimMapper.evidenceUrl(c.getId()))
                        : null;
        PlaceClaimViews.Reviewer reviewer = null;
        if (c.getReviewedByMemberId() != null) {
            MemberDirectory.MemberBrief r = members.get(c.getReviewedByMemberId());
            reviewer = new PlaceClaimViews.Reviewer(c.getReviewedByMemberId(), r == null ? null : r.loginId());
        }
        PlaceClaimViews.CurrentOwner currentOwner = null;
        if (owner != null) {
            MemberDirectory.MemberBrief o = members.get(owner.getOwnerMemberId());
            currentOwner =
                    new PlaceClaimViews.CurrentOwner(
                            owner.getOwnerMemberId(), o == null ? null : o.email(), ApiTimestamps.format(owner.getApprovedAt()));
        }
        long otherPending =
                claimRepository.countByPlaceIdAndStatusAndIdNot(c.getPlaceId(), PlaceClaimStatus.PENDING, c.getId());
        return new PlaceClaimViews.AdminClaimDetail(
                String.valueOf(c.getId()),
                c.getPlaceId(),
                placeName,
                applicant(c.getApplicantMemberId(), members),
                c.getStatus().name(),
                c.getContact(),
                c.getMemo(),
                c.getBusinessRegistrationNumber(),
                evidence,
                c.getRejectReason(),
                reviewer,
                ApiTimestamps.format(c.getReviewedAt()),
                currentOwner,
                otherPending,
                ApiTimestamps.format(c.getCreatedAt()));
    }

    private static PlaceClaimViews.Applicant applicant(String memberId, Map<String, MemberDirectory.MemberBrief> members) {
        MemberDirectory.MemberBrief b = members.get(memberId);
        return new PlaceClaimViews.Applicant(memberId, b == null ? null : b.email());
    }

    static PlaceClaimStatus parseStatusFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return PlaceClaimStatus.PENDING;
        }
        String v = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if ("ALL".equals(v)) {
            return null;
        }
        try {
            return PlaceClaimStatus.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
    }

    static Long parseClaimId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
