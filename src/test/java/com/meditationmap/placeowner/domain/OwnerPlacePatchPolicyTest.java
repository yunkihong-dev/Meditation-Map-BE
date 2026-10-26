package com.meditationmap.placeowner.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 오너 PATCH 허용 키·타입·중첩 보존 규칙 (계약 8.3, R16).
 *
 * <p>보장: 금지 키가 하나라도 있으면 전체 거부 + 위반 키 목록 · 허용 14키가 계약과 같다 · 후기(reviews)·숨김(hidden) 위조가
 * 무시되고 기존 값이 유지된다 · 새 항목은 reviews [] · 빠진 항목은 삭제된다.
 */
class OwnerPlacePatchPolicyTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    @DisplayName("editableFields 는 계약 8.2 의 14키 + 라운드 3 위치·사업자번호 5키(C4), 순서까지 같다")
    void 허용_키_목록() {
        assertThat(OwnerPlacePatchPolicy.EDITABLE_FIELDS)
                .containsExactly(
                        "name", "shortDescription", "description", "thumbnailUrl", "photos",
                        "hashtags", "duration", "admissionFee", "programs", "instructors",
                        "organization", "externalLink", "detailSections", "facilities",
                        "address", "addressDetail", "lat", "lng", "businessRegistrationNumber");
    }

    @Test
    @DisplayName("금지 키(address·hidden 등)가 하나라도 있으면 400 PLACE_OWNER_FIELD_NOT_ALLOWED + details.fields 목록")
    void 금지_키는_전체_거부() throws Exception {
        JsonNode patch = om.readTree("{\"name\":\"ok\",\"hidden\":true,\"regionId\":\"KR-11\",\"viewCount\":9}");

        assertThatThrownBy(() -> OwnerPlacePatchPolicy.validate(patch))
                .isInstanceOf(DetailedDomainException.class)
                .satisfies(e -> {
                    DetailedDomainException d = (DetailedDomainException) e;
                    assertThat(d.getErrorCode()).isEqualTo(ErrorCode.PLACE_OWNER_FIELD_NOT_ALLOWED);
                    assertThat(d.getDetails().get("fields")).isEqualTo(List.of("hidden", "regionId", "viewCount"));
                });
    }

    @Test
    @DisplayName("관리자 전용·집계 키는 모두 금지다")
    void 관리자_전용_키() throws Exception {
        for (String key : List.of("id", "regionId", "hidden", "venueKind", "themes",
                "hasTempleStay", "viewCount", "rating", "reviewCount", "unknownKey")) {
            JsonNode patch = om.readTree("{\"" + key + "\":1}");
            assertThatThrownBy(() -> OwnerPlacePatchPolicy.validate(patch))
                    .as(key)
                    .extracting(e -> ((DomainException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PLACE_OWNER_FIELD_NOT_ALLOWED);
        }
    }

    @Test
    @DisplayName("위치 묶음: address·lat·lng 는 함께. 하나만 오면 빠진 키가 details.fields 에 나온다")
    void 위치_묶음_필수() throws Exception {
        assertFields("{\"address\":\"서울특별시 종로구 1\"}", "lat", "lng");
        assertFields("{\"lat\":37.5,\"lng\":126.9}", "address");
    }

    @Test
    @DisplayName("좌표는 대한민국 범위의 숫자 — 0.0·문자열·해외 좌표 거부, 주소는 시·도로 시작")
    void 좌표_범위와_주소_형식() throws Exception {
        assertFields("{\"address\":\"서울특별시 종로구 1\",\"lat\":0.0,\"lng\":0.0}", "lat", "lng");
        assertFields("{\"address\":\"서울특별시 종로구 1\",\"lat\":\"37.5\",\"lng\":126.9}", "lat");
        assertFields("{\"address\":\"Tokyo 1-1\",\"lat\":35.68,\"lng\":139.76}", "address", "lng");
        assertFields("{\"address\":\"종로구 1\",\"lat\":37.5,\"lng\":126.9}", "address");
        OwnerPlacePatchPolicy.validate(om.readTree("{\"address\":\"경기 광주시 오포읍 1\",\"lat\":37.4,\"lng\":127.2}"));
        assertThat(OwnerPlacePatchPolicy.regionIdForPatch(om.readTree("{\"address\":\"경기 광주시 오포읍 1\"}")))
                .contains("KR-41");
        assertThat(OwnerPlacePatchPolicy.regionIdForPatch(om.readTree("{\"address\":\"광주광역시 북구 1\"}")))
                .contains("KR-29");
    }

    @Test
    @DisplayName("사업자번호는 숫자와 - 만(빈 문자열은 지우기), 상세주소는 100자 이하")
    void 사업자번호_상세주소() throws Exception {
        assertFields("{\"businessRegistrationNumber\":\"123-ab\"}", "businessRegistrationNumber");
        assertFields("{\"addressDetail\":\"" + "x".repeat(101) + "\"}", "addressDetail");
        OwnerPlacePatchPolicy.validate(om.readTree("{\"businessRegistrationNumber\":\"\",\"addressDetail\":\"4층\"}"));
        assertThat(OwnerPlacePatchPolicy.touchesPayload(om.readTree("{\"businessRegistrationNumber\":\"1\"}"))).isFalse();
    }

    private void assertFields(String json, String... keys) throws Exception {
        JsonNode patch = om.readTree(json);
        assertThatThrownBy(() -> OwnerPlacePatchPolicy.validate(patch))
                .isInstanceOf(DetailedDomainException.class)
                .satisfies(e -> {
                    DetailedDomainException d = (DetailedDomainException) e;
                    assertThat(d.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    @SuppressWarnings("unchecked")
                    Map<String, String> f = (Map<String, String>) d.getDetails().get("fields");
                    assertThat(f).containsOnlyKeys(keys);
                });
    }

    @Test
    @DisplayName("빈 객체는 400 VALIDATION_FAILED")
    void 빈_객체() throws Exception {
        assertThatThrownBy(() -> OwnerPlacePatchPolicy.validate(om.readTree("{}")))
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("타입 위반은 400 VALIDATION_FAILED + details.fields (name 빈값, URL 형식, 프로그램 id 누락·중복)")
    void 타입_검증() throws Exception {
        JsonNode patch =
                om.readTree(
                        "{\"name\":\" \",\"thumbnailUrl\":\"javascript:alert(1)\",\"photos\":[\"https://a\",3],"
                                + "\"programs\":[{\"id\":\"1\"},{\"id\":\"1\"},{\"title\":\"x\"}],"
                                + "\"instructors\":[{\"id\":\"i\",\"photoUrl\":\"ftp://x\"}]}");
        assertThatThrownBy(() -> OwnerPlacePatchPolicy.validate(patch))
                .isInstanceOf(DetailedDomainException.class)
                .satisfies(e -> {
                    DetailedDomainException d = (DetailedDomainException) e;
                    assertThat(d.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    @SuppressWarnings("unchecked")
                    Map<String, String> f = (Map<String, String>) d.getDetails().get("fields");
                    assertThat(f).containsKeys("name", "thumbnailUrl", "photos", "programs[1].id", "programs[2].id",
                            "instructors[0].photoUrl");
                });
    }

    @Test
    @DisplayName("허용 키만 있으면 통과한다 — GET 으로 받은 형태 그대로(null·빈 문자열 포함)")
    void 정상_패치() throws Exception {
        OwnerPlacePatchPolicy.validate(
                om.readTree(
                        "{\"name\":\"고요\",\"externalLink\":\"\",\"description\":null,\"organization\":{\"name\":\"o\"},"
                                + "\"programs\":[{\"id\":\"1\",\"imageUrl\":\"https://x\",\"imageUrls\":[\"http://y\"],"
                                + "\"reviews\":[{\"author\":\"a\"}],\"hidden\":false}],\"detailSections\":[]}"));
    }

    @Test
    @DisplayName("programs·instructors 의 reviews·programs[].hidden 위조는 무시되고 같은 id 의 기존 값이 유지된다")
    void 후기_숨김_보존() throws Exception {
        ObjectNode existing =
                (ObjectNode)
                        om.readTree(
                                "{\"id\":\"12\",\"name\":\"old\",\"address\":\"서울\",\"hidden\":true,"
                                        + "\"programs\":[{\"id\":\"1\",\"title\":\"a\",\"reviews\":[{\"author\":\"진짜\"}],\"hidden\":true},"
                                        + "{\"id\":\"2\",\"title\":\"지울 것\",\"reviews\":[{\"author\":\"r\"}]}],"
                                        + "\"instructors\":[{\"id\":\"i1\",\"name\":\"강사\",\"reviews\":[{\"author\":\"진짜강사후기\"}]}]}");
        ObjectNode patch =
                (ObjectNode)
                        om.readTree(
                                "{\"name\":\" new \",\"programs\":[{\"id\":\"1\",\"title\":\"a2\",\"reviews\":[{\"author\":\"가짜\"}],\"hidden\":false},"
                                        + "{\"id\":\"3\",\"title\":\"새것\",\"reviews\":[{\"author\":\"가짜\"}],\"hidden\":true}],"
                                        + "\"instructors\":[{\"id\":\"i1\",\"name\":\"강사2\",\"reviews\":[]}]}");

        ObjectNode merged = OwnerPlacePatchPolicy.merge(existing, patch);

        assertThat(merged.get("name").asText()).isEqualTo("new");
        assertThat(merged.get("address").asText()).as("patch 에 없는 키는 그대로").isEqualTo("서울");
        assertThat(merged.get("hidden").asBoolean()).isTrue();

        JsonNode p1 = merged.get("programs").get(0);
        assertThat(p1.get("title").asText()).isEqualTo("a2");
        assertThat(p1.get("reviews").get(0).get("author").asText()).isEqualTo("진짜");
        assertThat(p1.get("hidden").asBoolean()).isTrue();

        JsonNode p3 = merged.get("programs").get(1);
        assertThat(p3.get("id").asText()).isEqualTo("3");
        assertThat(p3.get("reviews")).isEmpty();
        assertThat(p3.has("hidden")).isFalse();

        assertThat(merged.get("programs")).hasSize(2); // id 2 는 요청에서 빠져 삭제
        assertThat(merged.get("instructors").get(0).get("reviews").get(0).get("author").asText())
                .isEqualTo("진짜강사후기");
        assertThat(existing.get("programs").get(0).get("title").asText()).as("원본 불변").isEqualTo("a");
    }
}
