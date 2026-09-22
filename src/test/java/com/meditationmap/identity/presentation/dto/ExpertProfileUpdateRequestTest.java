package com.meditationmap.identity.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `PUT /me/expert-profile` 요청 body 의 `hidden` 역직렬화를 고정한다 (계약 7-2절).
 *
 * <p>이 필드가 없던 시절 FE 는 `hidden` 을 보내고 있었고 Jackson 이 조용히 버렸다 — 전문가 본인의
 * 숨김 토글이 완전한 무동작이었다. 필드를 다시 지우면 이 테스트가 먼저 빨개진다.
 *
 * <h2>보장하는 것</h2>
 *
 * <ul>
 *   <li>`"hidden": true` 가 record 에 들어온다 (필드가 지워지면 실패)
 *   <li>키를 생략하면 record 의 `boolean` 기본값 `false` 로 해석된다 — 계약 Q-24 가 문서화한 동작이며
 *       예외가 아니다. 즉 <b>body 에서 `hidden` 을 빼면 노출 상태가 된다</b>
 * </ul>
 *
 * <h2>보장하지 않는 것</h2>
 *
 * <p>여기서 쓰는 ObjectMapper 는 스프링이 만든 것이 아니라 테스트가 직접 만든 것이다. 앱의 Jackson
 * 커스터마이징(예: `FAIL_ON_UNKNOWN_PROPERTIES` 설정 변경)까지 재현하지는 않는다. 저장 여부는
 * `MemberProfileApplicationServiceUpsertExpertTest` 가 따로 본다.
 */
class ExpertProfileUpdateRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String BODY_WITHOUT_HIDDEN =
            """
            {
              "name": "김명상",
              "intro": "MBSR 8주 과정을 운영합니다.",
              "degrees": ["OO대 상담심리 석사"],
              "certificates": ["MBSR 지도자"],
              "careers": ["OO명상원 10년"],
              "classTypes": ["MBSR"],
              "regionIds": ["KR-11"],
              "hasCenter": true,
              "centerName": "서울명상원",
              "centerAddress": "서울특별시 종로구 1",
              "businessRegistrationNumber": "123-45-67890",
              "businessOpeningDate": "2018-03-01"
            }
            """;

    @Test
    @DisplayName("hidden:true 를 보내면 요청 객체에 true 로 들어온다")
    void hidden_true_가_역직렬화된다() throws Exception {
        ExpertProfileUpdateRequest request = parse(withHidden("true"));

        assertThat(request.hidden()).isTrue();
        // 기존 필드가 같이 깨지지 않았는지 한 번 확인한다.
        assertThat(request.name()).isEqualTo("김명상");
        assertThat(request.hasCenter()).isTrue();
        assertThat(request.businessOpeningDate()).isEqualTo(java.time.LocalDate.of(2018, 3, 1));
    }

    @Test
    @DisplayName("hidden:false 를 보내면 false 로 들어온다")
    void hidden_false_가_역직렬화된다() throws Exception {
        assertThat(parse(withHidden("false")).hidden()).isFalse();
    }

    @Test
    @DisplayName("hidden 키를 생략하면 false(= 노출)로 해석된다 — 계약 Q-24 가 문서화한 동작")
    void hidden_을_생략하면_false_다() throws Exception {
        ExpertProfileUpdateRequest request = parse(BODY_WITHOUT_HIDDEN);

        assertThat(request.hidden()).isFalse();
    }

    private ExpertProfileUpdateRequest parse(String json) throws Exception {
        return objectMapper.readValue(json, ExpertProfileUpdateRequest.class);
    }

    private static String withHidden(String value) {
        return BODY_WITHOUT_HIDDEN.stripTrailing().replaceFirst("\\}$", ", \"hidden\": " + value + "}");
    }
}
