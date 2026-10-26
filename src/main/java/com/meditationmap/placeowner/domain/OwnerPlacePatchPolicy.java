package com.meditationmap.placeowner.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 오너 PATCH 규칙 (계약 8.3). 순수 로직이라 DB 없이 테스트한다.
 *
 * <ul>
 *   <li>허용 목록(editableFields)만. 주소·좌표는 묶음으로만 바뀌고 지역은 서버가 주소로 다시 정한다. 그 밖의 키가 하나라도 있으면 <b>전체 거부</b>(400 PLACE_OWNER_FIELD_NOT_ALLOWED,
 *       {@code details.fields} = 위반 키). 일부만 저장하지 않는다
 *   <li>담은 키는 최상위 단위로 통째로 교체(배열도 전체 교체)
 *   <li>후기 위조 방지(R16): {@code programs[]}·{@code instructors[]} 의 {@code reviews}, {@code programs[].hidden} 은
 *       요청 값을 무시하고 같은 id 의 기존 값을 유지한다. 새 id 면 {@code reviews: []}, hidden 없음
 * </ul>
 */
public final class OwnerPlacePatchPolicy {

    /** 계약 8.2 {@code editableFields} — 순서도 계약 그대로. */
    public static final List<String> EDITABLE_FIELDS =
            List.of(
                    "name",
                    "shortDescription",
                    "description",
                    "thumbnailUrl",
                    "photos",
                    "hashtags",
                    "duration",
                    "admissionFee",
                    "programs",
                    "instructors",
                    "organization",
                    "externalLink",
                    "detailSections",
                    "facilities",
                    // 라운드 3(사용자 결정): 오너가 주소·사업자번호를 직접 고친다.
                    "address",
                    "addressDetail",
                    "lat",
                    "lng",
                    "businessRegistrationNumber");

    /** 위치 묶음. 하나라도 보내면 셋 다 보내야 한다(좌표가 옛 값으로 남는 상태 금지). regionId 는 서버가 주소로 다시 정한다. */
    public static final List<String> LOCATION_GROUP = List.of("address", "lat", "lng");

    /** payload 가 아니라 별도 비공개 저장소(place_business_infos)로 가는 키. 공개 GET /places 에 나가지 않는다. */
    public static final String BUSINESS_REGISTRATION_NUMBER = "businessRegistrationNumber";

    /** 대한민국 범위(독도 포함, 여유 약간). 0·NaN·외국 좌표 차단용 — 지역 일치까지는 보지 않는다. */
    static final double MIN_LAT = 33.0, MAX_LAT = 38.7, MIN_LNG = 124.5, MAX_LNG = 132.0;
    static final int MAX_ADDRESS = 200;
    static final int MAX_ADDRESS_DETAIL = 100;
    private static final java.util.regex.Pattern BRN = java.util.regex.Pattern.compile("^[0-9-]{1,20}$");

    private static final Set<String> EDITABLE = Set.copyOf(EDITABLE_FIELDS);
    private static final Set<String> PLAIN_STRINGS =
            Set.of("shortDescription", "description", "duration", "admissionFee");
    private static final Set<String> URL_STRINGS = Set.of("thumbnailUrl", "externalLink");
    private static final Set<String> STRING_ARRAYS = Set.of("photos", "hashtags", "facilities");
    static final int MAX_PHOTOS = 30;
    static final int MAX_NAME = 100;

    private OwnerPlacePatchPolicy() {}

    /** 허용 키·타입 검증. 통과하면 아무것도 하지 않는다. */
    public static void validate(JsonNode patch) {
        if (patch == null || !patch.isObject() || patch.isEmpty()) {
            throw new DomainArgumentException(ErrorCode.VALIDATION_FAILED);
        }
        List<String> forbidden = new ArrayList<>();
        patch.fieldNames().forEachRemaining(k -> {
            if (!EDITABLE.contains(k)) {
                forbidden.add(k);
            }
        });
        if (!forbidden.isEmpty()) {
            forbidden.sort(null);
            throw DetailedDomainException.fields(ErrorCode.PLACE_OWNER_FIELD_NOT_ALLOWED, forbidden);
        }

        Map<String, String> errors = new LinkedHashMap<>();
        validateLocation(patch, errors);
        Iterator<Map.Entry<String, JsonNode>> it = patch.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            JsonNode v = e.getValue();
            if ("name".equals(key)) {
                if (!v.isTextual() || v.asText().trim().isEmpty() || v.asText().trim().length() > MAX_NAME) {
                    errors.put(key, "1~100자 문자열이어야 합니다.");
                }
                continue;
            }
            if (LOCATION_GROUP.contains(key)) {
                continue; // validateLocation 에서 묶음으로 본다
            }
            if (BUSINESS_REGISTRATION_NUMBER.equals(key)) {
                if (!v.isNull() && !(v.isTextual() && (v.asText().isEmpty() || BRN.matcher(v.asText().trim()).matches()))) {
                    errors.put(key, "숫자와 - 만, 20자 이하여야 합니다.");
                }
                continue;
            }
            if ("addressDetail".equals(key)) {
                if (!v.isNull() && (!v.isTextual() || v.asText().trim().length() > MAX_ADDRESS_DETAIL)) {
                    errors.put(key, "100자 이하 문자열이어야 합니다.");
                }
                continue;
            }
            if (v.isNull()) {
                continue;
            }
            if (PLAIN_STRINGS.contains(key)) {
                if (!v.isTextual()) {
                    errors.put(key, "문자열이어야 합니다.");
                }
            } else if (URL_STRINGS.contains(key)) {
                if (!isUrlText(v)) {
                    errors.put(key, "http(s):// 로 시작하는 주소 또는 빈 문자열이어야 합니다.");
                }
            } else if (STRING_ARRAYS.contains(key)) {
                if (!isStringArray(v)) {
                    errors.put(key, "문자열 배열이어야 합니다.");
                } else if ("photos".equals(key)) {
                    if (v.size() > MAX_PHOTOS) {
                        errors.put(key, "최대 " + MAX_PHOTOS + "장입니다.");
                    } else {
                        for (int i = 0; i < v.size(); i++) {
                            if (!isUrlText(v.get(i))) {
                                errors.put(key + "[" + i + "]", "http(s):// 로 시작하는 주소여야 합니다.");
                            }
                        }
                    }
                }
            } else if ("organization".equals(key)) {
                if (!v.isObject()) {
                    errors.put(key, "객체여야 합니다.");
                }
            } else if ("detailSections".equals(key)) {
                if (!v.isArray()) {
                    errors.put(key, "배열이어야 합니다.");
                }
            } else if ("programs".equals(key)) {
                validateItems(key, v, errors, Set.of("imageUrl"), Set.of("imageUrls"));
            } else if ("instructors".equals(key)) {
                validateItems(key, v, errors, Set.of("photoUrl"), Set.of());
            }
        }
        if (!errors.isEmpty()) {
            throw DetailedDomainException.fields(ErrorCode.VALIDATION_FAILED, errors);
        }
    }

    /**
     * 위치 묶음 검증: address·lat·lng 는 함께 오거나 함께 빠진다. 주소는 시·도로 시작해야 지역을 다시 정할 수 있다. 좌표는 대한민국
     * 범위의 숫자여야 한다(0.0·문자열 금지).
     */
    private static void validateLocation(JsonNode patch, Map<String, String> errors) {
        boolean any = LOCATION_GROUP.stream().anyMatch(patch::has);
        if (!any) {
            return;
        }
        for (String k : LOCATION_GROUP) {
            if (!patch.has(k) || patch.get(k).isNull()) {
                errors.put(k, "주소를 바꾸면 address·lat·lng 를 함께 보내야 합니다.");
            }
        }
        JsonNode address = patch.get("address");
        if (address != null && !address.isNull()) {
            String a = address.isTextual() ? address.asText().trim() : "";
            if (a.isEmpty() || a.length() > MAX_ADDRESS) {
                errors.put("address", "1~200자 문자열이어야 합니다.");
            } else if (com.meditationmap.region.domain.AddressRegionResolver.resolve(a).isEmpty()) {
                errors.put("address", "시·도(예: 서울특별시)로 시작하는 주소여야 합니다.");
            }
        }
        checkCoordinate(patch.get("lat"), "lat", MIN_LAT, MAX_LAT, errors);
        checkCoordinate(patch.get("lng"), "lng", MIN_LNG, MAX_LNG, errors);
    }

    private static void checkCoordinate(JsonNode v, String key, double min, double max, Map<String, String> errors) {
        if (v == null || v.isNull()) {
            return;
        }
        if (!v.isNumber() || !Double.isFinite(v.asDouble()) || v.asDouble() < min || v.asDouble() > max) {
            errors.put(key, "대한민국 범위의 숫자여야 합니다(" + min + "~" + max + ").");
        }
    }

    /** 주소가 바뀌면 다시 계산한 지역 ID. patch 에 위치 묶음이 없으면 empty. validate 통과 후에만 부른다. */
    public static java.util.Optional<String> regionIdForPatch(JsonNode patch) {
        JsonNode address = patch.get("address");
        if (address == null || address.isNull()) {
            return java.util.Optional.empty();
        }
        return com.meditationmap.region.domain.AddressRegionResolver.resolve(address.asText())
                .map(com.meditationmap.region.domain.RegionId::value);
    }

    /** patch 의 사업자등록번호. 키가 없으면 empty, 빈 문자열·null 이면 Optional.of("") (지우기). */
    public static java.util.Optional<String> businessRegistrationNumber(JsonNode patch) {
        if (!patch.has(BUSINESS_REGISTRATION_NUMBER)) {
            return java.util.Optional.empty();
        }
        JsonNode v = patch.get(BUSINESS_REGISTRATION_NUMBER);
        return java.util.Optional.of(v == null || v.isNull() ? "" : v.asText().trim());
    }

    /** payload 를 바꾸는 키가 하나라도 있는지(사업자번호만 고친 PATCH 는 장소 payload·캐시를 건드릴 필요가 없다). */
    public static boolean touchesPayload(JsonNode patch) {
        java.util.Iterator<String> names = patch.fieldNames();
        while (names.hasNext()) {
            if (!BUSINESS_REGISTRATION_NUMBER.equals(names.next())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 기존 payload 에 patch 를 적용한 새 객체. {@link #validate} 를 통과한 patch 만 넘긴다. id·regionId 고정과 프로그램
     * 정규화는 호출자가 한다.
     */
    public static ObjectNode merge(ObjectNode existing, ObjectNode patch) {
        ObjectNode out = existing.deepCopy();
        Iterator<Map.Entry<String, JsonNode>> it = patch.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            JsonNode v = e.getValue();
            if (BUSINESS_REGISTRATION_NUMBER.equals(key)) {
                continue; // payload 에 넣지 않는다(비공개 저장소)
            }
            if ("address".equals(key) || "addressDetail".equals(key)) {
                if (v.isNull()) {
                    out.remove(key);
                } else {
                    out.put(key, v.asText().trim());
                }
                continue;
            }
            if ("lat".equals(key) || "lng".equals(key)) {
                out.put(key, v.asDouble());
                continue;
            }
            if ("programs".equals(key) && v.isArray()) {
                out.set(key, mergeItems((ArrayNode) v, existing.get(key), true));
            } else if ("instructors".equals(key) && v.isArray()) {
                out.set(key, mergeItems((ArrayNode) v, existing.get(key), false));
            } else if ("name".equals(key)) {
                out.put(key, v.asText().trim());
            } else {
                out.set(key, v.deepCopy());
            }
        }
        return out;
    }

    private static ArrayNode mergeItems(ArrayNode incoming, JsonNode existingArray, boolean keepHidden) {
        Map<String, JsonNode> byId = new HashMap<>();
        if (existingArray != null && existingArray.isArray()) {
            for (JsonNode old : existingArray) {
                JsonNode id = old.get("id");
                if (id != null && id.isTextual()) {
                    byId.put(id.asText(), old);
                }
            }
        }
        ArrayNode result = incoming.arrayNode();
        for (JsonNode item : incoming) {
            ObjectNode copy = ((ObjectNode) item).deepCopy();
            JsonNode old = byId.get(copy.get("id").asText());
            JsonNode oldReviews = old == null ? null : old.get("reviews");
            if (oldReviews != null && oldReviews.isArray()) {
                copy.set("reviews", oldReviews.deepCopy());
            } else {
                copy.set("reviews", copy.arrayNode());
            }
            if (keepHidden) {
                JsonNode oldHidden = old == null ? null : old.get("hidden");
                if (oldHidden != null && !oldHidden.isNull()) {
                    copy.set("hidden", oldHidden.deepCopy());
                } else {
                    copy.remove("hidden");
                }
            }
            result.add(copy);
        }
        return result;
    }

    private static void validateItems(
            String key, JsonNode v, Map<String, String> errors, Set<String> urlFields, Set<String> urlArrayFields) {
        if (!v.isArray()) {
            errors.put(key, "배열이어야 합니다.");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < v.size(); i++) {
            JsonNode item = v.get(i);
            String path = key + "[" + i + "]";
            if (!item.isObject()) {
                errors.put(path, "객체여야 합니다.");
                continue;
            }
            JsonNode id = item.get("id");
            if (id == null || !id.isTextual() || id.asText().isBlank()) {
                errors.put(path + ".id", "필수 문자열입니다.");
            } else if (!seen.add(id.asText())) {
                errors.put(path + ".id", "배열 안에서 중복될 수 없습니다.");
            }
            for (String f : urlFields) {
                JsonNode u = item.get(f);
                if (u != null && !u.isNull() && !isUrlText(u)) {
                    errors.put(path + "." + f, "http(s):// 로 시작하는 주소 또는 빈 문자열이어야 합니다.");
                }
            }
            for (String f : urlArrayFields) {
                JsonNode arr = item.get(f);
                if (arr == null || arr.isNull()) {
                    continue;
                }
                if (!isStringArray(arr)) {
                    errors.put(path + "." + f, "문자열 배열이어야 합니다.");
                    continue;
                }
                for (int j = 0; j < arr.size(); j++) {
                    if (!isUrlText(arr.get(j))) {
                        errors.put(path + "." + f + "[" + j + "]", "http(s):// 로 시작하는 주소여야 합니다.");
                    }
                }
            }
        }
    }

    private static boolean isStringArray(JsonNode v) {
        if (!v.isArray()) {
            return false;
        }
        for (JsonNode x : v) {
            if (!x.isTextual()) {
                return false;
            }
        }
        return true;
    }

    static boolean isUrlText(JsonNode v) {
        if (!v.isTextual()) {
            return false;
        }
        String s = v.asText();
        return s.isEmpty() || s.startsWith("https://") || s.startsWith("http://");
    }
}
