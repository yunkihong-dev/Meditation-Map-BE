package com.meditationmap.placeowner.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 계약 4·5절 응답 모양. null 도 키는 내보낸다(계약 0절). 증빙 objectKey·URL 은 어디에도 없다. */
public final class PlaceClaimViews {

    private PlaceClaimViews() {}

    /** 4.1 MyPlaceClaim */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MyPlaceClaim(
            String id,
            String placeId,
            String placeName,
            String status,
            String contact,
            String memo,
            String businessRegistrationNumber,
            boolean hasEvidence,
            String evidenceFileName,
            String rejectReason,
            String createdAt,
            String reviewedAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Items<T>(List<T> items) {}

    /** 4.3 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ClaimStatus(
            String placeId,
            boolean hasOwner,
            @JsonProperty("isMine") boolean isMine,
            String myPendingClaimId,
            boolean canClaim) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Applicant(String memberId, String email) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Reviewer(String memberId, String loginId) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CurrentOwner(String memberId, String email, String approvedAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Evidence(String fileName, String contentType, Long sizeBytes, String url) {}

    /** 5.1 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminClaimSummary(
            String id,
            String placeId,
            String placeName,
            Applicant applicant,
            String status,
            boolean hasEvidence,
            String createdAt,
            String reviewedAt) {}

    /** 5.2 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminClaimDetail(
            String id,
            String placeId,
            String placeName,
            Applicant applicant,
            String status,
            String contact,
            String memo,
            String businessRegistrationNumber,
            Evidence evidence,
            String rejectReason,
            Reviewer reviewedBy,
            String reviewedAt,
            CurrentOwner placeCurrentOwner,
            long otherPendingCount,
            String createdAt) {}
}
