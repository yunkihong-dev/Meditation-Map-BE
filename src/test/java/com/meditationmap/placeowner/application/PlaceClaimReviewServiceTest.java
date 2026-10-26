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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.placeowner.domain.PlaceClaimStatus;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimSpringDataRepository;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceOwnerSpringDataRepository;
import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 승인·반려의 오류 매핑과 부수 효과를 고정한다 (계약 5.3·5.4, 12절 4항).
 *
 * <h2>보장하는 것</h2>
 * <ul>
 *   <li>{@code place_owners} PK 충돌({@link DataIntegrityViolationException})이 <b>409 PLACE_ALREADY_OWNED</b> 로 바뀐다.
 *       잡지 않으면 ApiExceptionHandler 가 500 DATA_ACCESS_FAILED 로 내보낸다(계약 위반)
 *   <li>이미 처리된 신청 → 409 PLACE_CLAIM_ALREADY_REVIEWED, 다른 신청 승인으로 자동 반려된 신청 → 409 PLACE_ALREADY_OWNED
 *   <li>승인하면 같은 장소의 다른 PENDING 이 사유와 함께 REJECTED 되고 active_key 가 비워지며 각자 알림이 나간다
 * </ul>
 *
 * <h2>보장하지 않는 것</h2>
 * <ul>
 *   <li>실제 MySQL 에서의 잠금·직렬화(동시 요청 경쟁). 이는 로컬 bootRun + 병렬 curl 로 실측했다(02_backend_report.md)
 *   <li>@Transactional 롤백 경계
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PlaceClaimReviewServiceTest {

    private static final String REVIEWER = "f277b1a5-8fa1-4b51-a5bb-54fdd8f91f68";
    private static final String APPLICANT = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER = "22222222-2222-2222-2222-222222222222";

    @Mock private PlaceClaimSpringDataRepository claimRepository;
    @Mock private PlaceOwnerSpringDataRepository ownerRepository;
    @Mock private PlaceCatalogGateway places;
    @Mock private PlaceClaimNotifier notifier;
    @Mock private PlaceClaimQueryService queryService;
    @Mock private com.meditationmap.placeowner.infrastructure.jpa.PlaceBusinessInfoSpringDataRepository businessInfoRepository;

    private PlaceClaimReviewService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T03:00:00Z"), ZoneOffset.UTC);
        service = new PlaceClaimReviewService(
                claimRepository, ownerRepository, places, notifier, queryService, businessInfoRepository, clock);
    }

    @Test
    @DisplayName("place_owners PK 충돌은 409 PLACE_ALREADY_OWNED 로 바뀐다 — 500 으로 새지 않는다")
    void pk_충돌은_PLACE_ALREADY_OWNED() {
        givenPendingClaim(57L, "12", APPLICANT);
        given(ownerRepository.findById("12")).willReturn(Optional.empty());
        given(ownerRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("Duplicate entry '12' for key 'PRIMARY'"));

        assertThatThrownBy(() -> service.approve(REVIEWER, "57"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_ALREADY_OWNED);
        assertThat(ErrorCode.PLACE_ALREADY_OWNED.getHttpStatus().value()).isEqualTo(409);
        verify(notifier, never()).approved(anyLong(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("이미 승인된 신청을 다시 승인하면 409 PLACE_CLAIM_ALREADY_REVIEWED")
    void 이미_승인된_신청() {
        PlaceClaimJpaEntity c = givenPendingClaim(57L, "12", APPLICANT);
        c.setStatus(PlaceClaimStatus.APPROVED);
        PlaceOwnerJpaEntity owner = owner("12", APPLICANT, 57L);
        given(ownerRepository.findById("12")).willReturn(Optional.of(owner));

        assertErrorCode(() -> service.approve(REVIEWER, "57"), ErrorCode.PLACE_CLAIM_ALREADY_REVIEWED);
        verify(ownerRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("다른 신청 승인으로 자동 반려된 신청을 승인하면 409 PLACE_ALREADY_OWNED")
    void 자동_반려된_신청() {
        PlaceClaimJpaEntity c = givenPendingClaim(58L, "12", OTHER);
        c.setStatus(PlaceClaimStatus.REJECTED);
        given(ownerRepository.findById("12")).willReturn(Optional.of(owner("12", APPLICANT, 57L)));

        assertErrorCode(() -> service.approve(REVIEWER, "58"), ErrorCode.PLACE_ALREADY_OWNED);
    }

    @Test
    @DisplayName("PENDING 이어도 장소에 오너가 이미 있으면 409 PLACE_ALREADY_OWNED (R11)")
    void 오너가_있는_장소의_대기_신청() {
        givenPendingClaim(59L, "12", OTHER);
        given(ownerRepository.findById("12")).willReturn(Optional.of(owner("12", APPLICANT, 57L)));

        assertErrorCode(() -> service.approve(REVIEWER, "59"), ErrorCode.PLACE_ALREADY_OWNED);
        verify(ownerRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("없는 신청은 404, 장소가 삭제된 신청도 404")
    void 없는_신청과_삭제된_장소() {
        given(claimRepository.findPlaceIdById(1L)).willReturn(Optional.empty());
        assertErrorCode(() -> service.approve(REVIEWER, "1"), ErrorCode.RESOURCE_NOT_FOUND);

        given(claimRepository.findPlaceIdById(2L)).willReturn(Optional.of("gone"));
        given(places.lock("gone")).willReturn(Optional.empty());
        assertErrorCode(() -> service.approve(REVIEWER, "2"), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("승인하면 오너 행이 생기고, 같은 장소의 다른 대기 신청은 사유와 함께 자동 반려되며 각자 알림이 나간다")
    void 승인_부수효과() {
        PlaceClaimJpaEntity claim = givenPendingClaim(57L, "12", APPLICANT);
        given(ownerRepository.findById("12")).willReturn(Optional.empty());
        PlaceClaimJpaEntity other = pending(58L, "12", OTHER);
        given(claimRepository.findOthersForUpdate("12", PlaceClaimStatus.PENDING, 57L))
                .willReturn(new ArrayList<>(List.of(other)));

        service.approve(REVIEWER, "57");

        ArgumentCaptor<PlaceOwnerJpaEntity> saved = ArgumentCaptor.forClass(PlaceOwnerJpaEntity.class);
        verify(ownerRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOwnerMemberId()).isEqualTo(APPLICANT);
        assertThat(saved.getValue().getClaimId()).isEqualTo(57L);
        assertThat(saved.getValue().isNew()).as("새 오너 행은 merge 가 아니라 persist(INSERT)로 가야 한다").isTrue();

        assertThat(claim.getStatus()).isEqualTo(PlaceClaimStatus.APPROVED);
        assertThat(claim.getActiveKey()).isNull();
        assertThat(claim.getReviewedByMemberId()).isEqualTo(REVIEWER);

        assertThat(other.getStatus()).isEqualTo(PlaceClaimStatus.REJECTED);
        assertThat(other.getActiveKey()).isNull();
        assertThat(other.getRejectReason()).isEqualTo("다른 신청이 승인되었습니다");

        verify(notifier).approved(57L, APPLICANT, "12", "고요명상센터");
        verify(notifier).rejected(eq(58L), eq(OTHER), eq("고요명상센터"), eq("다른 신청이 승인되었습니다"));
    }

    @Test
    @DisplayName("반려: 사유가 비면 400 VALIDATION_FAILED, 처리된 신청이면 409, 성공하면 신청자에게 사유가 담긴 알림")
    void 반려() {
        assertErrorCode(() -> service.reject(REVIEWER, "57", "  "), ErrorCode.VALIDATION_FAILED);
        assertErrorCode(() -> service.reject(REVIEWER, "57", "x".repeat(501)), ErrorCode.VALIDATION_FAILED);

        PlaceClaimJpaEntity done = pending(60L, "12", OTHER);
        done.setStatus(PlaceClaimStatus.REJECTED);
        given(claimRepository.findByIdForUpdate(60L)).willReturn(Optional.of(done));
        assertErrorCode(() -> service.reject(REVIEWER, "60", "사유"), ErrorCode.PLACE_CLAIM_ALREADY_REVIEWED);

        PlaceClaimJpaEntity c = pending(61L, "12", OTHER);
        given(claimRepository.findByIdForUpdate(61L)).willReturn(Optional.of(c));
        given(places.find("12")).willReturn(Optional.of(place("12")));
        service.reject(REVIEWER, "61", "증빙 서류로 대표자 확인이 어렵습니다.");
        assertThat(c.getStatus()).isEqualTo(PlaceClaimStatus.REJECTED);
        assertThat(c.getActiveKey()).isNull();
        verify(notifier).rejected(61L, OTHER, "고요명상센터", "증빙 서류로 대표자 확인이 어렵습니다.");
    }

    // --- helpers ---

    private PlaceClaimJpaEntity givenPendingClaim(Long id, String placeId, String applicant) {
        PlaceClaimJpaEntity c = pending(id, placeId, applicant);
        given(claimRepository.findPlaceIdById(id)).willReturn(Optional.of(placeId));
        given(places.lock(placeId)).willReturn(Optional.of(place(placeId)));
        given(claimRepository.findByIdForUpdate(id)).willReturn(Optional.of(c));
        return c;
    }

    private static PlaceClaimJpaEntity pending(Long id, String placeId, String applicant) {
        PlaceClaimJpaEntity c = new PlaceClaimJpaEntity();
        c.setId(id);
        c.setPlaceId(placeId);
        c.setApplicantMemberId(applicant);
        c.setStatus(PlaceClaimStatus.PENDING);
        c.setActiveKey(PlaceClaimJpaEntity.activeKeyOf(placeId, applicant));
        c.setContact("010");
        c.setCreatedAt(Instant.parse("2026-10-07T02:00:00Z"));
        return c;
    }

    private static PlaceOwnerJpaEntity owner(String placeId, String member, Long claimId) {
        PlaceOwnerJpaEntity o = new PlaceOwnerJpaEntity();
        o.setPlaceId(placeId);
        o.setOwnerMemberId(member);
        o.setClaimId(claimId);
        return o;
    }

    private static PlaceJpaEntity place(String id) {
        PlaceJpaEntity p = new PlaceJpaEntity();
        p.setId(id);
        p.setRegionId("seoul");
        p.setData(new ObjectMapper().createObjectNode().put("id", id).put("name", "고요명상센터"));
        return p;
    }

    private static void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
