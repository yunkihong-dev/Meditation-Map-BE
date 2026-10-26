package com.meditationmap.placeowner.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meditationmap.place.application.PlaceProgramNormalizer;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.placeowner.domain.OwnerPlacePatchPolicy;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceBusinessInfoJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceBusinessInfoSpringDataRepository;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerSpringDataRepository;
import com.meditationmap.shared.domain.ApiTimestamps;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오너 장소 조회·수정 (계약 8절).
 *
 * <p>수정은 공개 목록·상세 캐시를 바꾸므로 {@code @CacheEvict({"places","place"}, allEntries=true)} 를 건다
 * (관리자 저장 {@code AdminCatalogService} 와 같은 방식). 셀프 호출이면 프록시를 타지 않으므로 컨트롤러가 이 빈을 직접 부른다.
 *
 * <p>알려진 한계(D4): 관리자 PUT 은 payload 를 통째로 교체하고 락·버전이 없다. 관리자가 열어 둔 편집 폼으로 나중에 저장하면
 * 오너 수정이 덮인다.
 */
@Service
@RequiredArgsConstructor
public class OwnerPlaceService {

    private final PlaceOwnerSpringDataRepository ownerRepository;
    private final PlaceCatalogGateway places;
    private final PlaceBusinessInfoSpringDataRepository businessInfoRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Items myPlaces(String memberId) {
        List<PlaceOwnerJpaEntity> owned = ownerRepository.findByOwnerMemberIdOrderByApprovedAtDesc(memberId);
        Map<String, PlaceJpaEntity> placeMap = places.findAll(owned.stream().map(PlaceOwnerJpaEntity::getPlaceId).toList());
        List<OwnedPlaceSummary> items = new ArrayList<>();
        for (PlaceOwnerJpaEntity o : owned) {
            PlaceJpaEntity p = placeMap.get(o.getPlaceId());
            if (p == null) {
                continue; // 장소가 삭제된 고아 행(R12)
            }
            JsonNode data = p.getData();
            items.add(
                    new OwnedPlaceSummary(
                            p.getId(),
                            p.getRegionId(),
                            PlaceCatalogGateway.nameOf(p),
                            PlaceCatalogGateway.textOrNull(data, "thumbnailUrl"),
                            data != null && data.path("hidden").isBoolean() && data.path("hidden").booleanValue(),
                            ApiTimestamps.format(o.getApprovedAt())));
        }
        return new Items(items);
    }

    @Transactional(readOnly = true)
    public OwnedPlace get(String memberId, String placeId) {
        PlaceJpaEntity place =
                places.find(placeId).orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        requireOwner(memberId, placeId);
        return view(place, businessNumber(placeId));
    }

    @Transactional
    @CacheEvict(value = {"places", "place"}, allEntries = true)
    public OwnedPlace update(String memberId, String placeId, JsonNode patch) {
        // 첫 조회를 잠금 조회로 한다 — 앞서 일반 조회로 엔티티를 올려 두면 잠금 뒤에도 그 (오래된) 상태를 병합 기준으로 쓴다.
        PlaceJpaEntity place =
                places.lock(placeId).orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        requireOwner(memberId, placeId);
        OwnerPlacePatchPolicy.validate(patch);

        OwnerPlacePatchPolicy.businessRegistrationNumber(patch)
                .ifPresent(brn -> saveBusinessNumber(placeId, memberId, brn));

        if (OwnerPlacePatchPolicy.touchesPayload(patch)) {
            JsonNode current = place.getData();
            ObjectNode base =
                    current != null && current.isObject()
                            ? (ObjectNode) current
                            : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            ObjectNode merged = OwnerPlacePatchPolicy.merge(base, (ObjectNode) patch);
            // 주소가 바뀌면 지역도 주소로 다시 정한다(컬럼 + payload 둘 다). 아니면 기존 값 고정.
            String regionId = OwnerPlacePatchPolicy.regionIdForPatch(patch).orElse(place.getRegionId());
            place.setRegionId(regionId);
            merged.put("id", place.getId());
            merged.put("regionId", regionId);
            PlaceProgramNormalizer.normalizePlaceData(merged);
            place.setData(merged);
            place = places.save(place);
        }
        return view(place, businessNumber(placeId));
    }

    /** 장소가 없으면 404, 오너가 아니면 403. 통계 등 오너 전용 조회 앞에서 쓴다. */
    @Transactional(readOnly = true)
    public void requireOwnedPlace(String memberId, String placeId) {
        places.find(placeId).orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        requireOwner(memberId, placeId);
    }

    private String businessNumber(String placeId) {
        return businessInfoRepository
                .findById(placeId)
                .map(PlaceBusinessInfoJpaEntity::getBusinessRegistrationNumber)
                .filter(v -> !v.isBlank())
                .orElse(null);
    }

    private void saveBusinessNumber(String placeId, String memberId, String brn) {
        PlaceBusinessInfoJpaEntity row =
                businessInfoRepository.findById(placeId).orElseGet(() -> {
                    PlaceBusinessInfoJpaEntity e = new PlaceBusinessInfoJpaEntity();
                    e.setPlaceId(placeId);
                    return e;
                });
        row.setBusinessRegistrationNumber(brn.isEmpty() ? null : brn);
        row.setUpdatedByMemberId(memberId);
        row.setUpdatedAt(clock.instant());
        businessInfoRepository.save(row);
    }

    private void requireOwner(String memberId, String placeId) {
        boolean mine =
                ownerRepository
                        .findById(placeId)
                        .map(o -> o.getOwnerMemberId().equals(memberId))
                        .orElse(false);
        if (!mine) {
            throw new DomainArgumentException(ErrorCode.PLACE_NOT_OWNER);
        }
    }

    private static OwnedPlace view(PlaceJpaEntity place, String businessRegistrationNumber) {
        JsonNode data = place.getData();
        JsonNode out = data;
        if (data instanceof ObjectNode obj) {
            out = PlaceProgramNormalizer.normalizePlaceData(obj.deepCopy());
        }
        return new OwnedPlace(
                place.getId(), place.getRegionId(), out, businessRegistrationNumber, OwnerPlacePatchPolicy.EDITABLE_FIELDS);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OwnedPlaceSummary(
            String placeId, String regionId, String name, String thumbnailUrl, boolean hidden, String approvedAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Items(List<OwnedPlaceSummary> items) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    /**
     * 8.2 응답. {@code businessRegistrationNumber} 는 payload({@code data}) 밖에 둔다 — 비공개 값이라 공개 상세에 섞이면 안 된다
     * (계약 변경 C5).
     */
    public record OwnedPlace(
            String placeId,
            String regionId,
            JsonNode data,
            String businessRegistrationNumber,
            List<String> editableFields) {}
}
