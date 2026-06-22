package com.meditationmap.identity.infrastructure.phone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meditationmap.identity.application.port.out.PhoneAuthSmsSender;
import com.meditationmap.identity.application.support.KoreanMobileNormalizer;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 네이버 클라우드 SENS SMS/LMS 발송.
 *
 * <p>OTP 문구에 한글이 포함되므로 본문 길이 한도를 피하기 위해 {@code LMS} 타입을 사용합니다.
 */
@Slf4j
@RequiredArgsConstructor
public class NaverSensPhoneSignupSmsSender implements PhoneAuthSmsSender {

    private static final MediaType JSON_UTF8 =
            new MediaType("application", "json", StandardCharsets.UTF_8);

    private final PhoneNaverSmsProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public void sendSignupOtp(String e164Digits, String otpCodeDigits) {
        validateConfig();

        String domesticTo = e164DigitsToDomesticKR(e164Digits);
        String fromDigits = normalizePhoneDigits(props.getFrom());
        String text =
                "[명상맵] 인증번호는 [" + otpCodeDigits + "] 입니다. 5분 이내 입력해 주세요.";
        ObjectNode body = buildRequestBody(fromDigits, domesticTo, text);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());

        RestClient client =
                RestClient.builder()
                        .requestFactory(new BufferingClientHttpRequestFactory(factory))
                        .build();

        String path = "/sms/v2/services/" + props.getServiceId() + "/messages";
        String fullUrl = trimTrailingSlash(props.getBaseUrl().trim()) + path;
        String timestamp = Long.toString(System.currentTimeMillis());
        String signature = sign("POST", path, timestamp);

        try {
            client.post()
                    .uri(URI.create(fullUrl))
                    .contentType(JSON_UTF8)
                    .acceptCharset(StandardCharsets.UTF_8)
                    .header("x-ncp-apigw-timestamp", timestamp)
                    .header("x-ncp-iam-access-key", props.getAccessKey().trim())
                    .header("x-ncp-apigw-signature-v2", signature)
                    .headers(h -> h.add(HttpHeaders.USER_AGENT, "meditation-map-be/sens"))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.debug(
                    "SENS LMS 발송 요청 접수 완료 to(masked)={}",
                    KoreanMobileNormalizer.toPlusE164(e164Digits).replaceAll(".{4}$", "****"));
        } catch (RestClientResponseException ex) {
            log.warn(
                    "SENS API 실패 status={} body={}",
                    ex.getStatusCode().value(),
                    truncate(ex.getResponseBodyAsString(), 500));
            throw new InfrastructureException(ErrorCode.EXTERNAL_SERVICE_ERROR, ex);
        } catch (RuntimeException ex) {
            throw new InfrastructureException(ErrorCode.EXTERNAL_SERVICE_ERROR, ex);
        }
    }

    private void validateConfig() {
        if (!StringUtils.hasText(props.getServiceId())) {
            throw new IllegalStateException(
                    "sms-provider=naver 인데 NAVER_SMS_ID(app.auth.phone.naver.service-id) 가 비었습니다.");
        }
        if (!StringUtils.hasText(props.getFrom())) {
            throw new IllegalStateException(
                    "sms-provider=naver 인데 NAVER_SMS_PHONE_NUMBER(app.auth.phone.naver.from) 이 비었습니다.");
        }
        if (!StringUtils.hasText(props.getAccessKey())) {
            throw new IllegalStateException(
                    "sms-provider=naver 인데 NAVER_ACCESS_KEY(app.auth.phone.naver.access-key) 가 비었습니다.");
        }
        if (!StringUtils.hasText(props.getSecretKey())) {
            throw new IllegalStateException(
                    "sms-provider=naver 인데 NAVER_SECRET_KEY(app.auth.phone.naver.secret-key) 가 비었습니다.");
        }
    }

    private ObjectNode buildRequestBody(
            String fromDigits, String domesticToDigits, String text) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("to", domesticToDigits);
        msg.put("content", text);

        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(msg);

        return objectMapper
                .createObjectNode()
                .put("type", "LMS")
                .put("contentType", "COMM")
                .put("countryCode", "82")
                .put("from", fromDigits)
                .put("content", text)
                .set("messages", messages);
    }

    /** {@code 821012345678} → {@code 01012345678} */
    private static String e164DigitsToDomesticKR(String e164Digits) {
        if (e164Digits == null || !e164Digits.startsWith("82") || e164Digits.length() < 3) {
            throw new IllegalArgumentException("e164 한국 휴대전화 형식이 아닙니다.");
        }
        return "0" + e164Digits.substring(2);
    }

    private static String normalizePhoneDigits(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^0-9]", "");
    }

    private static String trimTrailingSlash(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * <a href="https://api.ncloud-docs.com/docs/en/common-ncpapi">공통 시그니처</a>:
     * {@code METHOD + " " + url + "\\n" + timestamp + "\\n" + accessKey}
     */
    private String sign(String method, String urlPathIncludingQueryIfAny, String timestampMs) {
        try {
            String message =
                    method
                            + " "
                            + urlPathIncludingQueryIfAny
                            + "\n"
                            + timestampMs
                            + "\n"
                            + props.getAccessKey().trim();
            SecretKeySpec keySpec =
                    new SecretKeySpec(
                            props.getSecretKey().trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("SENS 서명 생성 실패", ex);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
