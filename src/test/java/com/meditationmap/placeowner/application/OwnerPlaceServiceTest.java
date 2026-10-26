package com.meditationmap.placeowner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceBusinessInfoJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceBusinessInfoSpringDataRepository;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerSpringDataRepository;
import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;

/**
 * 오너 권한·저장 경계 (계약 8.2·8.3·7절).
 *
 * <p>보장: 오너가 아니면 403 PLACE_NOT_OWNER 이고 저장하지 않는다 · 금지 키가 있으면 DB 변경 없음 · 저장 시 id·regionId 는
 * 기존 값으로 고정된다 · update 에 {@code @CacheEvict({"places","place"}, allEntries=true)} 가 붙어 있다.
 * <p>보장하지 않음: 프록시를 통한 실제 캐시 삭제(런타임 실측은 보고서), 행 잠금.
 */
@ExtendWith(MockitoExtension.class)
class OwnerPlaceServiceTest {

    private static final String OWNER = "11111111-1111-1111-1111-111111111111";
    private static final String STRANGER = "22222222-2222-2222-2222-222222222222";

    @Mock private PlaceOwnerSpringDataRepository ownerRepository;
    @Mock private PlaceCatalogGateway places;
    @Mock private PlaceBusinessInfoSpringDataRepository businessInfoRepository;

    private final ObjectMapper om = new ObjectMapper();
    private OwnerPlaceService service;

    @BeforeEach
    void setUp() {
        service =
                new OwnerPlaceService(
                        ownerRepository,
                        places,
                        businessInfoRepository,
                        Clock.fixed(Instant.parse("2026-10-07T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("오너가 아니면 GET·PATCH 모두 403 PLACE_NOT_OWNER, 저장하지 않는다")
    void 오너_아님() throws Exception {
        given(places.find("12")).willReturn(Optional.of(place()));
        given(places.lock("12")).willReturn(Optional.of(place()));
        given(ownerRepository.findById("12")).willReturn(Optional.of(owner(OWNER)));

        assertCode(() -> service.get(STRANGER, "12"), ErrorCode.PLACE_NOT_OWNER);
        assertCode(() -> service.update(STRANGER, "12", om.readTree("{\"name\":\"x\"}")), ErrorCode.PLACE_NOT_OWNER);
        verify(places, never()).save(any());
        assertThat(ErrorCode.PLACE_NOT_OWNER.getHttpStatus().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("금지 키가 섞이면 400 이고 장소를 저장하지 않는다")
    void 금지_키는_저장_안함() throws Exception {
        given(places.lock("12")).willReturn(Optional.of(place()));
        given(ownerRepository.findById("12")).willReturn(Optional.of(owner(OWNER)));

        assertCode(
                () -> service.update(OWNER, "12", om.readTree("{\"shortDescription\":\"x\",\"hidden\":true}")),
                ErrorCode.PLACE_OWNER_FIELD_NOT_ALLOWED);
        verify(places, never()).save(any());
    }

    @Test
    @DisplayName("없는 장소는 404")
    void 없는_장소() throws Exception {
        given(places.lock("999")).willReturn(Optional.empty());
        assertCode(() -> service.update(OWNER, "999", om.readTree("{\"name\":\"x\"}")), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("저장 시 id·regionId 는 기존 값으로 고정되고 허용 필드만 바뀐다")
    void 저장() throws Exception {
        PlaceJpaEntity p = place();
        given(places.lock("12")).willReturn(Optional.of(p));
        given(ownerRepository.findById("12")).willReturn(Optional.of(owner(OWNER)));
        given(places.save(any())).willAnswer(inv -> inv.getArgument(0));

        OwnerPlaceService.OwnedPlace res =
                service.update(OWNER, "12", om.readTree("{\"shortDescription\":\"새 소개\"}"));

        assertThat(res.placeId()).isEqualTo("12");
        assertThat(res.data().get("shortDescription").asText()).isEqualTo("새 소개");
        assertThat(res.data().get("id").asText()).isEqualTo("12");
        assertThat(res.data().get("regionId").asText()).isEqualTo("seoul");
        assertThat(res.data().get("address").asText()).isEqualTo("서울");
        assertThat(res.editableFields()).hasSize(19);
    }

    @Test
    @DisplayName("update 에 places·place 전체 무효화(@CacheEvict)가 붙어 있다 (계약 7절)")
    void 캐시_무효화_선언() throws Exception {
        Method m = OwnerPlaceService.class.getMethod("update", String.class, String.class,
                com.fasterxml.jackson.databind.JsonNode.class);
        CacheEvict evict = m.getAnnotation(CacheEvict.class);
        assertThat(evict).isNotNull();
        assertThat(evict.value()).containsExactlyInAnyOrder("places", "place");
        assertThat(evict.allEntries()).isTrue();
    }

    @Test
    @DisplayName("주소를 바꾸면 좌표·지역(컬럼+payload)이 함께 바뀐다 — 주소만 바뀌고 좌표가 옛 값으로 남지 않는다")
    void 주소_변경_좌표_지역_동반() throws Exception {
        PlaceJpaEntity p = place();
        ((ObjectNode) p.getData()).put("lat", 37.5).put("lng", 126.9);
        given(places.lock("12")).willReturn(Optional.of(p));
        given(ownerRepository.findById("12")).willReturn(Optional.of(owner(OWNER)));
        given(places.save(any())).willAnswer(inv -> inv.getArgument(0));

        OwnerPlaceService.OwnedPlace res =
                service.update(
                        OWNER,
                        "12",
                        om.readTree(
                                "{\"address\":\"부산광역시 해운대구 우동 1\",\"addressDetail\":\"4층 402호\",\"lat\":35.163,\"lng\":129.163}"));

        assertThat(p.getRegionId()).isEqualTo("KR-26");
        assertThat(res.regionId()).isEqualTo("KR-26");
        assertThat(res.data().get("regionId").asText()).isEqualTo("KR-26");
        assertThat(res.data().get("address").asText()).isEqualTo("부산광역시 해운대구 우동 1");
        assertThat(res.data().get("addressDetail").asText()).isEqualTo("4층 402호");
        assertThat(res.data().get("lat").asDouble()).isEqualTo(35.163);
        assertThat(res.data().get("lng").asDouble()).isEqualTo(129.163);
    }

    @Test
    @DisplayName("주소만 보내고 좌표를 빼면 400, 장소는 저장하지 않는다")
    void 주소만_변경은_거부() throws Exception {
        given(places.lock("12")).willReturn(Optional.of(place()));
        given(ownerRepository.findById("12")).willReturn(Optional.of(owner(OWNER)));

        assertCode(() -> service.update(OWNER, "12", om.readTree("{\"address\":\"부산광역시 해운대구\"}")),
                ErrorCode.VALIDATION_FAILED);
        verify(places, never()).save(any());
    }

    @Test
    @DisplayName("사업자번호는 payload 가 아니라 비공개 저장소에 들어가고, 그것만 바꾸면 장소 payload 는 저장하지 않는다")
    void 사업자번호_분리_저장() throws Exception {
        PlaceJpaEntity p = place();
        given(places.lock("12")).willReturn(Optional.of(p));
        given(ownerRepository.findById("12")).willReturn(Optional.of(owner(OWNER)));
        given(businessInfoRepository.findById("12")).willReturn(Optional.empty());
        org.mockito.ArgumentCaptor<PlaceBusinessInfoJpaEntity> saved =
                org.mockito.ArgumentCaptor.forClass(PlaceBusinessInfoJpaEntity.class);

        OwnerPlaceService.OwnedPlace res =
                service.update(OWNER, "12", om.readTree("{\"businessRegistrationNumber\":\"123-45-67890\"}"));

        verify(businessInfoRepository).save(saved.capture());
        assertThat(saved.getValue().getBusinessRegistrationNumber()).isEqualTo("123-45-67890");
        assertThat(res.data().has("businessRegistrationNumber")).as("payload(공개 상세)에 넣지 않는다").isFalse();
        verify(places, never()).save(any());
    }

    private PlaceJpaEntity place() {
        PlaceJpaEntity p = new PlaceJpaEntity();
        p.setId("12");
        p.setRegionId("seoul");
        ObjectNode data = om.createObjectNode().put("id", "12").put("regionId", "seoul").put("name", "고요").put("address", "서울");
        p.setData(data);
        return p;
    }

    private static PlaceOwnerJpaEntity owner(String memberId) {
        PlaceOwnerJpaEntity o = new PlaceOwnerJpaEntity();
        o.setPlaceId("12");
        o.setOwnerMemberId(memberId);
        o.setClaimId(1L);
        return o;
    }

    private static void assertCode(ThrowingRunnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
