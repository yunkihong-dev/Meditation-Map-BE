package com.meditationmap.centerchat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.centerchat.domain.InquirySettingsPolicy.KakaoType;
import com.meditationmap.centerchat.domain.InquirySettingsPolicy.Mode;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** C14 카카오 링크 형식 검증 — 정상 / 다른 도메인 / http / 경로 변형. */
class InquirySettingsPolicyTest {

    private final ObjectMapper om = new ObjectMapper();

    private JsonNode body(String json) {
        try {
            return om.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://open.kakao.com/o/gAbC1234", "https://open.kakao.com/o/sX_y-9"})
    @DisplayName("오픈채팅 정상 링크")
    void 오픈채팅_정상(String url) {
        assertThat(InquirySettingsPolicy.validUrl(KakaoType.OPEN_CHAT, url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://pf.kakao.com/_xkxkxK", "https://pf.kakao.com/_xkxkxK/chat"})
    @DisplayName("채널 정상 링크(/chat 접미 허용)")
    void 채널_정상(String url) {
        assertThat(InquirySettingsPolicy.validUrl(KakaoType.CHANNEL, url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://open.kakao.com/o/gAbC1234", // http
        "https://open.kakao.com.evil.com/o/gAbC1234", // 다른 도메인(접미)
        "https://evil.com/open.kakao.com/o/gAbC1234", // 다른 도메인
        "https://kakao.com/o/gAbC1234",
        "https://OPEN.KAKAO.COM/o/gAbC1234", // 호스트 대소문자
        "https://open.kakao.com/me/someone", // 다른 경로(프로필)
        "https://open.kakao.com/o/", // id 없음
        "https://open.kakao.com/o/gAbC1234/", // 끝 슬래시
        "https://open.kakao.com/o/gAbC1234/extra", // 경로 추가
        "https://open.kakao.com/o/gAbC1234?x=1", // 쿼리
        "https://open.kakao.com/o/gAbC1234#a", // 조각
        "https://open.kakao.com:8443/o/gAbC1234", // 포트
        "https://user@open.kakao.com/o/gAbC1234", // userinfo
        "https://open.kakao.com/o/../o/gAbC1234", // 경로 조작
        "https://pf.kakao.com/_xkxkxK", // 종류 불일치(채널 링크)
        "javascript:alert(1)"
    })
    @DisplayName("오픈채팅 거부")
    void 오픈채팅_거부(String url) {
        assertThat(InquirySettingsPolicy.validUrl(KakaoType.OPEN_CHAT, url)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://pf.kakao.com/_xkxkxK",
        "https://pf.kakao.com.evil.com/_xkxkxK",
        "https://pf.kakao.com/_xkxkxK/chat/more",
        "https://pf.kakao.com/_xkxkxK/friend",
        "https://pf.kakao.com/",
        "https://pf.kakao.com/_xkxkxK/",
        "https://pf.kakao.com/_xkxkxK?a=b",
        "https://open.kakao.com/o/gAbC1234" // 종류 불일치
    })
    @DisplayName("채널 거부")
    void 채널_거부(String url) {
        assertThat(InquirySettingsPolicy.validUrl(KakaoType.CHANNEL, url)).isFalse();
    }

    @Test
    @DisplayName("KAKAO + 정상 링크 → 통과(앞뒤 공백 제거)")
    void 파싱_카카오() {
        InquirySettingsPolicy.Settings s = InquirySettingsPolicy.parse(
                body("{\"mode\":\"KAKAO\",\"kakaoType\":\"CHANNEL\",\"kakaoUrl\":\"  https://pf.kakao.com/_ab/chat \"}"));
        assertThat(s).isEqualTo(new InquirySettingsPolicy.Settings(Mode.KAKAO, KakaoType.CHANNEL, "https://pf.kakao.com/_ab/chat"));
    }

    @Test
    @DisplayName("CHAT 은 카카오 키 없이 통과, 둘 다 보내면 검증 후 보관")
    void 파싱_채팅() {
        assertThat(InquirySettingsPolicy.parse(body("{\"mode\":\"CHAT\"}")))
                .isEqualTo(new InquirySettingsPolicy.Settings(Mode.CHAT, null, null));
        assertThat(InquirySettingsPolicy.parse(
                        body("{\"mode\":\"CHAT\",\"kakaoType\":\"OPEN_CHAT\",\"kakaoUrl\":\"https://open.kakao.com/o/g1\"}")))
                .isEqualTo(new InquirySettingsPolicy.Settings(Mode.CHAT, KakaoType.OPEN_CHAT, "https://open.kakao.com/o/g1"));
    }

    @Test
    @DisplayName("400 VALIDATION_FAILED details.fields — 모드·종류·링크")
    void 파싱_거부() {
        assertFields("{}", "mode");
        assertFields("{\"mode\":\"PHONE\"}", "mode");
        assertFields("{\"mode\":\"KAKAO\"}", "kakaoType", "kakaoUrl");
        assertFields("{\"mode\":\"KAKAO\",\"kakaoType\":\"OPEN_CHAT\",\"kakaoUrl\":\"https://pf.kakao.com/_ab\"}", "kakaoUrl");
        assertFields("{\"mode\":\"KAKAO\",\"kakaoType\":\"CHANNEL\",\"kakaoUrl\":\"http://pf.kakao.com/_ab\"}", "kakaoUrl");
        assertFields("{\"mode\":\"KAKAO\",\"kakaoType\":\"LINE\",\"kakaoUrl\":\"https://pf.kakao.com/_ab\"}", "kakaoType");
        assertFields("{\"mode\":\"KAKAO\",\"kakaoType\":\"CHANNEL\",\"kakaoUrl\":5}", "kakaoUrl");
        assertFields("{\"mode\":\"CHAT\",\"kakaoType\":\"CHANNEL\"}", "kakaoUrl");
        assertFields("{\"mode\":\"CHAT\",\"kakaoType\":\"CHANNEL\",\"kakaoUrl\":\"https://evil.com/x\"}", "kakaoUrl");
        assertFields("[]", "mode");
    }

    @SuppressWarnings("unchecked")
    private void assertFields(String json, String... keys) {
        assertThatThrownBy(() -> InquirySettingsPolicy.parse(body(json)))
                .isInstanceOf(DetailedDomainException.class)
                .satisfies(e -> {
                    DetailedDomainException d = (DetailedDomainException) e;
                    assertThat(d.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(((Map<String, Object>) d.getDetails().get("fields"))).containsOnlyKeys(keys);
                });
    }
}
