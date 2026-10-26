package com.meditationmap.placeowner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.placeowner.domain.PlaceClaimStatus;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimSpringDataRepository;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerSpringDataRepository;
import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.storage.application.port.out.ObjectStoragePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 소유 신청 검증과 409 매핑 (계약 4.1). DB 없이 돈다.
 *
 * <p>보장: active_key 유니크 충돌(동시 더블클릭)이 409 PLACE_CLAIM_ALREADY_PENDING 이 된다(500 아님) · 오너가 있는 장소는
 * 409 · 증빙 형식·크기·스토리지 비활성 코드 · 증빙은 비공개 키로 올라가고 응답에 키/URL 이 없다.
 * <p>보장하지 않음: 멀티파트 바인딩(컨트롤러 테스트 몫), 실제 S3.
 */
@ExtendWith(MockitoExtension.class)
class PlaceClaimSubmitServiceTest {

    private static final String MEMBER = "11111111-1111-1111-1111-111111111111";

    @Mock private PlaceClaimSpringDataRepository claimRepository;
    @Mock private PlaceOwnerSpringDataRepository ownerRepository;
    @Mock private PlaceCatalogGateway places;
    @Mock private ObjectProvider<ObjectStoragePort> storageProvider;
    @Mock private ObjectStoragePort storage;
    @Mock private MemberDirectory memberDirectory;
    @Mock private PlaceClaimNotifier notifier;

    private PlaceClaimSubmitService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T03:00:00Z"), ZoneOffset.UTC);
        service =
                new PlaceClaimSubmitService(
                        claimRepository, ownerRepository, places, storageProvider, memberDirectory, notifier, clock);
    }

    @Test
    @DisplayName("active_key 유니크 충돌(동시 중복 신청)은 409 PLACE_CLAIM_ALREADY_PENDING")
    void 동시_중복_신청은_409() {
        givenPlaceWithoutOwner("12");
        given(claimRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_place_claims_active_key'"));

        assertCode(() -> service.submit(MEMBER, input("12", null)), ErrorCode.PLACE_CLAIM_ALREADY_PENDING);
        verify(notifier, never()).submitted(anyLong(), any(), any());
    }

    @Test
    @DisplayName("내 대기 신청이 이미 있으면 409 PLACE_CLAIM_ALREADY_PENDING")
    void 대기_신청_존재() {
        given(places.find("12")).willReturn(Optional.of(place("12")));
        given(ownerRepository.existsById("12")).willReturn(false);
        given(claimRepository.findFirstByPlaceIdAndApplicantMemberIdAndStatus("12", MEMBER, PlaceClaimStatus.PENDING))
                .willReturn(Optional.of(new PlaceClaimJpaEntity()));

        assertCode(() -> service.submit(MEMBER, input("12", null)), ErrorCode.PLACE_CLAIM_ALREADY_PENDING);
    }

    @Test
    @DisplayName("승인된 오너가 있으면 409 PLACE_ALREADY_OWNED, 장소가 없으면 404")
    void 오너_있음_장소_없음() {
        given(places.find("12")).willReturn(Optional.of(place("12")));
        given(ownerRepository.existsById("12")).willReturn(true);
        assertCode(() -> service.submit(MEMBER, input("12", null)), ErrorCode.PLACE_ALREADY_OWNED);

        given(places.find("404")).willReturn(Optional.empty());
        assertCode(() -> service.submit(MEMBER, input("404", null)), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("필수 누락은 400 VALIDATION_FAILED + details.fields")
    void 필수_누락() {
        assertThatThrownBy(() -> service.submit(MEMBER, new ClaimSubmission(" ", null, null, "12a", null)))
                .isInstanceOf(DetailedDomainException.class)
                .satisfies(e -> {
                    DetailedDomainException d = (DetailedDomainException) e;
                    assertThat(d.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    @SuppressWarnings("unchecked")
                    Map<String, String> fields = (Map<String, String>) d.getDetails().get("fields");
                    assertThat(fields).containsOnlyKeys("placeId", "contact", "businessRegistrationNumber");
                });
        verifyNoInteractions(claimRepository, places);
    }

    @Test
    @DisplayName("증빙: 형식 불가 400, 10MB 초과 413, 스토리지 비활성 503")
    void 증빙_오류() {
        assertCode(() -> service.submit(MEMBER, input("12", evidence("text/plain", 10))), ErrorCode.CLAIM_EVIDENCE_TYPE_NOT_ALLOWED);
        assertCode(
                () -> service.submit(MEMBER, input("12", evidence("application/pdf", 10L * 1024 * 1024 + 1))),
                ErrorCode.UPLOAD_TOO_LARGE);
        assertThat(ErrorCode.UPLOAD_TOO_LARGE.getHttpStatus().value()).isEqualTo(413);

        given(places.find("12")).willReturn(Optional.of(place("12")));
        given(ownerRepository.existsById("12")).willReturn(false);
        given(storageProvider.getIfAvailable()).willReturn(null);
        assertCode(() -> service.submit(MEMBER, input("12", evidence("image/png", 10))), ErrorCode.FILE_STORAGE_DISABLED);
        assertThat(ErrorCode.FILE_STORAGE_DISABLED.getHttpStatus().value()).isEqualTo(503);
    }

    @Test
    @DisplayName("증빙은 private 네임스페이스로 올라가고, 회원 응답에는 키·URL 없이 파일 이름만 있다. 스태프 알림이 나간다")
    void 증빙_비공개_저장() {
        givenPlaceWithoutOwner("12");
        given(storageProvider.getIfAvailable()).willReturn(storage);
        given(storage.putPrivate(eq("place-claims"), anyString(), eq("application/pdf"), any()))
                .willReturn(new ObjectStoragePort.StoredObject("private/place-claims/2026-10-07/u-a.pdf", null));
        given(claimRepository.saveAndFlush(any())).willAnswer(inv -> {
            PlaceClaimJpaEntity c = inv.getArgument(0);
            c.setId(57L);
            return c;
        });
        given(memberDirectory.findBrief(MEMBER))
                .willReturn(Optional.of(new MemberDirectory.MemberBrief(MEMBER, "owner@example.com", null, null)));

        PlaceClaimViews.MyPlaceClaim res =
                service.submit(MEMBER, input("12", evidence("application/pdf; charset=binary", 100)));

        assertThat(res.id()).isEqualTo("57");
        assertThat(res.status()).isEqualTo("PENDING");
        assertThat(res.hasEvidence()).isTrue();
        assertThat(res.evidenceFileName()).isEqualTo("사업자등록증.pdf");
        assertThat(res.toString()).doesNotContain("private/");
        verify(notifier).submitted(57L, "고요명상센터", "owner@example.com");
    }

    // --- helpers ---

    private void givenPlaceWithoutOwner(String id) {
        given(places.find(id)).willReturn(Optional.of(place(id)));
        given(places.lock(id)).willReturn(Optional.of(place(id)));
        given(ownerRepository.existsById(id)).willReturn(false);
        given(claimRepository.findFirstByPlaceIdAndApplicantMemberIdAndStatus(id, MEMBER, PlaceClaimStatus.PENDING))
                .willReturn(Optional.empty());
    }

    private static ClaimSubmission input(String placeId, ClaimSubmission.EvidenceFile ev) {
        return new ClaimSubmission(placeId, "010-1234-5678", "센터 대표입니다.", "123-45-67890", ev);
    }

    private static ClaimSubmission.EvidenceFile evidence(String type, long size) {
        return new ClaimSubmission.EvidenceFile("사업자등록증.pdf", type, size, new byte[] {1, 2, 3});
    }

    private static PlaceJpaEntity place(String id) {
        PlaceJpaEntity p = new PlaceJpaEntity();
        p.setId(id);
        p.setRegionId("seoul");
        p.setData(new ObjectMapper().createObjectNode().put("id", id).put("name", "고요명상센터"));
        return p;
    }

    private static void assertCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
