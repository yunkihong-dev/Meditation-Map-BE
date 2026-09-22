package com.meditationmap.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.expert.infrastructure.jpa.ExpertJpaEntity;
import com.meditationmap.expert.infrastructure.jpa.ExpertSpringDataRepository;
import com.meditationmap.expert.infrastructure.jpa.ExpertVerificationSpringDataRepository;
import com.meditationmap.identity.infrastructure.jpa.MemberProfileSpringDataRepository;
import com.meditationmap.identity.presentation.dto.ExpertProfileUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * `upsertExpert()` 가 전문가 본인의 숨김 토글을 실제로 저장하고, 그러면서 기존 payload 를 지우지
 * 않는지 고정한다 (계약 7-2절, 판정 Q-20·Q-23).
 *
 * <p>DB 없이 돈다. 리포지토리 3개만 모킹하고 `ObjectMapper` 는 진짜를 쓴다 — 검증 대상인 `data`
 * 조립은 순수 자바 로직이라 그대로 실행된다.
 *
 * <h2>보장하는 것</h2>
 *
 * <ul>
 *   <li>`hidden:true` 가 `data.hidden` 에 기록된다. `data.put("hidden", ...)` 한 줄이 지워지면 실패한다
 *   <li>`hidden:false` 로 되돌리면 `false` 가 기록된다
 *   <li><b>기존 payload 가 보존된다</b> — `adminNote`·`centerPlaceId`·`programs`·`reviews` 가
 *       살아남는다. `deepCopy` 대신 새 ObjectNode 로 시작하도록 바꾸면 여기서 잡힌다
 * </ul>
 *
 * <h2>보장하지 않는 것</h2>
 *
 * <ul>
 *   <li><b>캐시 무효화</b>. `@CacheEvict` 는 스프링 프록시가 있어야 동작하는데 여기서는 객체를 직접
 *       생성한다. "저장 직후 목록에 반영되는가"(Q-21·Q-22)는 이 테스트 밖이다
 *   <li>MySQL JSON 컬럼에 실제로 어떤 형태로 저장되는지. `ExpertJpaEntity.setData()` 까지만 본다
 *   <li>`@Transactional` 경계, 인증·권한
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MemberProfileApplicationServiceUpsertExpertTest {

    private static final String MEMBER_ID = "ac64ad84-3395-4989-bdd4-fc49cb884e57";

    @Mock private MemberProfileSpringDataRepository profileRepo;
    @Mock private ExpertSpringDataRepository expertRepo;
    @Mock private ExpertVerificationSpringDataRepository verificationRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MemberProfileApplicationService service;

    @BeforeEach
    void setUp() {
        service =
                new MemberProfileApplicationService(
                        profileRepo, expertRepo, verificationRepo, objectMapper);
    }

    @Test
    @DisplayName("hidden:true 가 payload 에 저장된다")
    void hidden_true_가_payload_에_저장된다() throws Exception {
        givenExistingExpert();

        JsonNode saved = service.upsertExpert(MEMBER_ID, "https://cdn.test/a.png", request(true)).getData();

        assertThat(saved.get("hidden").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("hidden:false 로 되돌리면 false 가 저장된다")
    void hidden_false_로_되돌릴_수_있다() throws Exception {
        givenExistingExpert();

        JsonNode saved = service.upsertExpert(MEMBER_ID, "https://cdn.test/a.png", request(false)).getData();

        assertThat(saved.get("hidden").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("숨김 저장이 기존 payload 를 지우지 않는다 — adminNote·centerPlaceId·programs·reviews 보존")
    void 기존_payload_가_보존된다() throws Exception {
        givenExistingExpert();

        JsonNode saved = service.upsertExpert(MEMBER_ID, "https://cdn.test/a.png", request(true)).getData();

        // 관리자가 넣어 둔 필드는 전문가 본인 저장으로 사라지면 안 된다.
        assertThat(saved.get("adminNote").asText()).isEqualTo("관리자가 넣은 메모");
        assertThat(saved.get("centerPlaceId").asText()).isEqualTo("qa-place-01");
        assertThat(saved.get("programs").size()).isEqualTo(1);
        assertThat(saved.get("programs").get(0).get("id").asText()).isEqualTo("ep9");
        assertThat(saved.get("reviews").size()).isEqualTo(1);
        assertThat(saved.get("reviews").get(0).get("id").asText()).isEqualTo("rv9");
        // 요청으로 덮어쓰는 필드는 새 값이 들어간다.
        assertThat(saved.get("name").asText()).isEqualTo("김명상");
        assertThat(saved.get("avatarUrl").asText()).isEqualTo("https://cdn.test/a.png");
    }

    // --- fixtures ------------------------------------------------------------------------

    private void givenExistingExpert() throws Exception {
        ExpertJpaEntity existing = new ExpertJpaEntity();
        existing.setId(MEMBER_ID);
        existing.setOwnerMemberId(MEMBER_ID);
        existing.setData(
                objectMapper.readTree(
                        """
                        {
                          "id": "%s",
                          "name": "예전 이름",
                          "hidden": false,
                          "adminNote": "관리자가 넣은 메모",
                          "centerPlaceId": "qa-place-01",
                          "programs": [{"id": "ep9", "title": "기존 프로그램"}],
                          "reviews": [{"id": "rv9", "author": "후기 작성자"}]
                        }
                        """
                                .formatted(MEMBER_ID)));

        given(expertRepo.findByOwnerMemberId(MEMBER_ID)).willReturn(Optional.of(existing));
        willAnswer(invocation -> invocation.getArgument(0))
                .given(expertRepo)
                .save(any(ExpertJpaEntity.class));
    }

    /**
     * 사업자 정보는 비워 둔다 — 채우면 `verificationRepo` 경로까지 함께 타서 이 테스트가 보는 범위가
     * 흐려진다. `hasCenter` 는 true 로 둔다(false 면 `centerPlaceId` 를 의도적으로 제거하는 별도 분기다).
     */
    private static ExpertProfileUpdateRequest request(boolean hidden) {
        return new ExpertProfileUpdateRequest(
                "김명상",
                "MBSR 8주 과정을 운영합니다.",
                List.of("OO대 상담심리 석사"),
                List.of("MBSR 지도자"),
                List.of("OO명상원 10년"),
                List.of("MBSR"),
                List.of("KR-11"),
                true,
                "서울명상원",
                "서울특별시 종로구 1",
                null,
                null,
                hidden);
    }
}
