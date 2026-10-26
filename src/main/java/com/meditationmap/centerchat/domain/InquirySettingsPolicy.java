package com.meditationmap.centerchat.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 장소 문의 방식(C14). CHAT(기본) | KAKAO. KAKAO 면 종류(OPEN_CHAT | CHANNEL)와 그 종류의 링크 형식만 받는다.
 *
 * <p>링크 형식(앞뒤 공백만 잘라 내고 그 외 변형은 거부 — 호스트 대소문자·http·다른 경로·쿼리·조각·끝 슬래시 모두 400):
 * <ul>
 *   <li>OPEN_CHAT: {@code https://open.kakao.com/o/<id>}
 *   <li>CHANNEL: {@code https://pf.kakao.com/<id>} 또는 {@code https://pf.kakao.com/<id>/chat}
 * </ul>
 * {@code <id>} = 영문·숫자·{@code _}·{@code -} 1~64자.
 */
public final class InquirySettingsPolicy {

    private InquirySettingsPolicy() {}

    public enum Mode { CHAT, KAKAO }

    public enum KakaoType { OPEN_CHAT, CHANNEL }

    public static final int MAX_URL_LENGTH = 500;

    private static final Pattern OPEN_CHAT = Pattern.compile("^https://open\\.kakao\\.com/o/[A-Za-z0-9_-]{1,64}$");
    private static final Pattern CHANNEL = Pattern.compile("^https://pf\\.kakao\\.com/[A-Za-z0-9_-]{1,64}(/chat)?$");

    /** 검증을 통과한 PUT 본문. CHAT 이면 kakaoType·kakaoUrl 은 보낸 값을 보관만 한다(되돌릴 때 다시 입력하지 않게). */
    public record Settings(Mode mode, KakaoType kakaoType, String kakaoUrl) {}

    public static boolean validUrl(KakaoType type, String url) {
        if (type == null || url == null || url.length() > MAX_URL_LENGTH) {
            return false;
        }
        return (type == KakaoType.OPEN_CHAT ? OPEN_CHAT : CHANNEL).matcher(url).matches();
    }

    /**
     * PUT 본문 검증. 실패하면 400 VALIDATION_FAILED, details.fields 에 키별 사유. 모르는 키는 무시한다.
     *
     * <ul>
     *   <li>mode: 필수, "CHAT" | "KAKAO"
     *   <li>KAKAO: kakaoType 필수("OPEN_CHAT" | "CHANNEL"), kakaoUrl 필수 + 형식
     *   <li>CHAT: kakaoType·kakaoUrl 은 선택. 보내면(null 아님) 같은 규칙으로 검증하고 보관한다. 둘 중 하나만 보내면 400
     * </ul>
     */
    public static Settings parse(JsonNode body) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (body == null || !body.isObject()) {
            errors.put("mode", "필수입니다.");
            throw DetailedDomainException.fields(ErrorCode.VALIDATION_FAILED, errors);
        }
        Mode mode = enumOf(Mode.class, body.get("mode"));
        if (mode == null) {
            errors.put("mode", "CHAT 또는 KAKAO 여야 합니다.");
        }
        JsonNode typeNode = body.get("kakaoType");
        JsonNode urlNode = body.get("kakaoUrl");
        boolean typeGiven = typeNode != null && !typeNode.isNull();
        boolean urlGiven = urlNode != null && !urlNode.isNull();
        boolean kakaoRequired = mode == Mode.KAKAO;

        KakaoType type = null;
        if (typeGiven || kakaoRequired) {
            type = enumOf(KakaoType.class, typeNode);
            if (type == null) {
                errors.put("kakaoType", "OPEN_CHAT 또는 CHANNEL 이어야 합니다.");
            }
        }
        String url = null;
        if (urlGiven || kakaoRequired) {
            url = urlNode != null && urlNode.isTextual() ? urlNode.asText().trim() : null;
            if (url == null || url.isEmpty()) {
                errors.put("kakaoUrl", "필수입니다.");
            } else if (type != null && !validUrl(type, url)) {
                errors.put("kakaoUrl", type == KakaoType.OPEN_CHAT
                        ? "https://open.kakao.com/o/… 형식의 오픈채팅 링크여야 합니다."
                        : "https://pf.kakao.com/… 형식의 카카오톡 채널 링크여야 합니다.");
            }
        }
        if (mode == Mode.CHAT && typeGiven != urlGiven) {
            errors.putIfAbsent(typeGiven ? "kakaoUrl" : "kakaoType", "kakaoType 과 kakaoUrl 은 함께 보내야 합니다.");
        }
        if (!errors.isEmpty()) {
            throw DetailedDomainException.fields(ErrorCode.VALIDATION_FAILED, errors);
        }
        return new Settings(mode, type, url);
    }

    private static <E extends Enum<E>> E enumOf(Class<E> cls, JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        for (E e : cls.getEnumConstants()) {
            if (e.name().equals(node.asText())) {
                return e;
            }
        }
        return null;
    }
}
