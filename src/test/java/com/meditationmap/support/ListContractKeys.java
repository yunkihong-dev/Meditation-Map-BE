package com.meditationmap.support;

import java.util.Set;

/**
 * 공개 목록 응답의 키 집합 — 계약 `_workspace/01_contract.md` 4-3절(place)·5-3절(expert)을 코드로 옮긴 것.
 *
 * <p>여기 적힌 집합이 기준이다. 투영에 필드를 더하거나 빼면 이 상수를 쓰는 테스트가 전부 빨개진다.
 * 그때 할 일은 테스트를 고치는 것이 아니라 계약 문서를 먼저 고치고 그 결과를 여기에 반영하는 것이다.
 *
 * <p><b>이 상수가 보장하지 않는 것</b>: 값의 정확성, 그리고 어떤 행이 목록에 포함되는지(= 숨김 필터).
 * 포함 여부는 MySQL 의 JSON 함수 평가 결과이며 이 저장소의 어떤 자동 테스트도 아직 덮지 못한다.
 */
public final class ListContractKeys {

    /** `GET /places` 목록 원소의 키 23개. `hidden` 은 여기에 없다(계약 12절 2항). */
    public static final Set<String> PLACE_LIST_KEYS =
            Set.of(
                    "id",
                    "regionId",
                    "name",
                    "shortDescription",
                    "description",
                    "address",
                    "lat",
                    "lng",
                    "thumbnailUrl",
                    "hashtags",
                    "themes",
                    "hasTempleStay",
                    "duration",
                    "admissionFee",
                    "venueKind",
                    "organization",
                    "viewCount",
                    "rating",
                    "reviewCount",
                    "externalLink",
                    "programs",
                    "instructors",
                    "detailSections");

    /**
     * 좌표가 없는 행의 키 21개. `lat`/`lng` 는 0.0 으로 채우지 않고 키 자체를 뺀다(계약 12절 8항).
     */
    public static final Set<String> PLACE_LIST_KEYS_WITHOUT_COORDINATES =
            minus(PLACE_LIST_KEYS, "lat", "lng");

    /** `GET /experts` 목록 원소의 키 16개. `hidden` 도 `regionId`(단수)도 없다(계약 5-3절). */
    public static final Set<String> EXPERT_LIST_KEYS =
            Set.of(
                    "id",
                    "name",
                    "avatarUrl",
                    "intro",
                    "specialties",
                    "regionIds",
                    "hasCenter",
                    "centerSummary",
                    "centerPlaceId",
                    "classTypes",
                    "activityAreas",
                    "degrees",
                    "certificates",
                    "careers",
                    "programs",
                    "reviews");

    /** 센터 정보가 비어 있는 전문가의 키 14개 — `centerSummary`·`centerPlaceId` 는 빈 값이면 빠진다. */
    public static final Set<String> EXPERT_LIST_KEYS_WITHOUT_CENTER =
            minus(EXPERT_LIST_KEYS, "centerSummary", "centerPlaceId");

    private static Set<String> minus(Set<String> base, String... removed) {
        Set<String> copy = new java.util.HashSet<>(base);
        for (String key : removed) {
            copy.remove(key);
        }
        return Set.copyOf(copy);
    }

    private ListContractKeys() {}
}
