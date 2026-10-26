package com.meditationmap.placeowner.application;

import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.placeowner.application.ClaimSubmission.EvidenceFile;
import com.meditationmap.placeowner.domain.PlaceClaimStatus;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimSpringDataRepository;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerSpringDataRepository;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import com.meditationmap.storage.application.port.out.ObjectStoragePort;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소유 신청 (계약 4.1).
 *
 * <ol>
 *   <li>입력 검증 → 400 VALIDATION_FAILED({@code details.fields})
 *   <li>장소 없음 404 / 승인된 오너 있음 409 PLACE_ALREADY_OWNED / 내 대기 신청 있음 409 PLACE_CLAIM_ALREADY_PENDING
 *   <li>증빙은 비공개 키로 S3 에 먼저 올린다(DB 실패 시 객체가 고아로 남는 것은 감수 — 계획 3.3)
 *   <li>장소 행을 잠그고 오너를 다시 확인한 뒤 INSERT. {@code active_key} 유니크 충돌은 409 로 바꾼다(500 금지)
 *   <li>커밋 후 스태프 전원에게 PLACE_CLAIM_SUBMITTED
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class PlaceClaimSubmitService {

    public static final long MAX_EVIDENCE_BYTES = 10L * 1024 * 1024;
    static final Set<String> ALLOWED_EVIDENCE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "application/pdf");
    static final String EVIDENCE_NAMESPACE = "place-claims";
    private static final Pattern BRN = Pattern.compile("^[0-9-]{1,20}$");

    private final PlaceClaimSpringDataRepository claimRepository;
    private final PlaceOwnerSpringDataRepository ownerRepository;
    private final PlaceCatalogGateway places;
    private final ObjectProvider<ObjectStoragePort> storage;
    private final MemberDirectory memberDirectory;
    private final PlaceClaimNotifier notifier;
    private final Clock clock;

    /** READ COMMITTED — 장소 잠금 뒤 오너 재확인이 최신 커밋을 읽도록(승인 서비스와 같은 이유). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlaceClaimViews.MyPlaceClaim submit(String memberId, ClaimSubmission in) {
        Normalized n = validate(in);

        PlaceJpaEntity place =
                places.find(n.placeId).orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        if (ownerRepository.existsById(n.placeId)) {
            throw new DomainArgumentException(ErrorCode.PLACE_ALREADY_OWNED);
        }
        if (claimRepository
                .findFirstByPlaceIdAndApplicantMemberIdAndStatus(n.placeId, memberId, PlaceClaimStatus.PENDING)
                .isPresent()) {
            throw new DomainArgumentException(ErrorCode.PLACE_CLAIM_ALREADY_PENDING);
        }

        ObjectStoragePort.StoredObject stored = null;
        EvidenceFile ev = in.evidence();
        if (ev != null) {
            ObjectStoragePort port = storage.getIfAvailable();
            if (port == null) {
                throw new DomainArgumentException(ErrorCode.FILE_STORAGE_DISABLED);
            }
            try {
                stored = port.putPrivate(EVIDENCE_NAMESPACE, ev.originalFilename(), n.evidenceType, ev.bytes());
            } catch (RuntimeException e) {
                throw new InfrastructureException(ErrorCode.FILE_STORE_FAILED, e);
            }
        }

        // 신청·승인 경쟁: 승인 트랜잭션도 이 행을 잠그므로, 여기서 다시 본 오너 상태는 최신이다.
        places.lock(n.placeId).orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        if (ownerRepository.existsById(n.placeId)) {
            throw new DomainArgumentException(ErrorCode.PLACE_ALREADY_OWNED);
        }

        PlaceClaimJpaEntity claim = new PlaceClaimJpaEntity();
        claim.setPlaceId(n.placeId);
        claim.setApplicantMemberId(memberId);
        claim.setStatus(PlaceClaimStatus.PENDING);
        claim.setActiveKey(PlaceClaimJpaEntity.activeKeyOf(n.placeId, memberId));
        claim.setContact(n.contact);
        claim.setMemo(n.memo);
        claim.setBusinessRegistrationNumber(n.brn);
        if (stored != null) {
            claim.setEvidenceObjectKey(stored.objectKey());
            claim.setEvidenceFileName(safeFileName(ev.originalFilename()));
            claim.setEvidenceContentType(n.evidenceType);
            claim.setEvidenceSizeBytes(ev.size());
        }
        claim.setCreatedAt(clock.instant());
        try {
            claim = claimRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException e) {
            // uk_place_claims_active_key — 같은 회원·같은 장소 동시 신청(더블클릭)
            throw new DomainArgumentException(ErrorCode.PLACE_CLAIM_ALREADY_PENDING);
        }

        String placeName = PlaceCatalogGateway.nameOf(place);
        String email = memberDirectory.findBrief(memberId).map(MemberDirectory.MemberBrief::email).orElse(null);
        notifier.submitted(claim.getId(), placeName, email);
        return PlaceClaimMapper.toMine(claim, placeName);
    }

    private Normalized validate(ClaimSubmission in) {
        Map<String, String> errors = new LinkedHashMap<>();
        String placeId = trimToNull(in.placeId());
        if (placeId == null) {
            errors.put("placeId", "필수입니다.");
        } else if (placeId.length() > 255) {
            errors.put("placeId", "255자 이하여야 합니다.");
        }
        String contact = trimToNull(in.contact());
        if (contact == null) {
            errors.put("contact", "필수입니다.");
        } else if (contact.length() > 100) {
            errors.put("contact", "100자 이하여야 합니다.");
        }
        String memo = trimToNull(in.memo());
        if (memo != null && memo.length() > 2000) {
            errors.put("memo", "2000자 이하여야 합니다.");
        }
        String brn = trimToNull(in.businessRegistrationNumber());
        if (brn != null && !BRN.matcher(brn).matches()) {
            errors.put("businessRegistrationNumber", "숫자와 - 만, 20자 이하여야 합니다.");
        }
        if (!errors.isEmpty()) {
            throw DetailedDomainException.fields(ErrorCode.VALIDATION_FAILED, errors);
        }
        String evidenceType = null;
        EvidenceFile ev = in.evidence();
        if (ev != null) {
            if (ev.size() > MAX_EVIDENCE_BYTES) {
                throw new DomainArgumentException(ErrorCode.UPLOAD_TOO_LARGE);
            }
            evidenceType = normalizeContentType(ev.contentType());
            if (!ALLOWED_EVIDENCE_TYPES.contains(evidenceType)) {
                throw new DomainArgumentException(ErrorCode.CLAIM_EVIDENCE_TYPE_NOT_ALLOWED);
            }
        }
        return new Normalized(placeId, contact, memo, brn, evidenceType);
    }

    static String normalizeContentType(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.toLowerCase(Locale.ROOT).trim();
        int semi = t.indexOf(';');
        if (semi >= 0) {
            t = t.substring(0, semi).trim();
        }
        return "image/jpg".equals(t) ? "image/jpeg" : t;
    }

    static String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "evidence";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        name = name.replaceAll("[\\p{Cntrl}\"]", "_");
        if (name.isEmpty()) {
            return "evidence";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private record Normalized(String placeId, String contact, String memo, String brn, String evidenceType) {}
}
