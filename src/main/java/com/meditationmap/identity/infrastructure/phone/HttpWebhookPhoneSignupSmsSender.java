package com.meditationmap.identity.infrastructure.phone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meditationmap.identity.application.port.out.PhoneAuthSmsSender;
import com.meditationmap.identity.application.support.KoreanMobileNormalizer;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * 사설 서버·타 클라우드의 문자 게이트웨이로 JSON POST 합니다.
 *
 * <p>바디 형식 고정(JSON):
 *
 * <pre>
 * {
 *   "e164Digits": "821012345678",
 *   "e164International": "+821012345678",
 *   "otpCode": "123456",
 *   "text": "[명상맵] 인증번호는 [123456] 입니다. 5분 이내 입력해 주세요."
 * }
 * </pre>
 *
 * 게이트웨이에서 필드 이름을 받아 각 SMS 대행사 API로 변환하면 됩니다.
 */
@Slf4j
@RequiredArgsConstructor
public class HttpWebhookPhoneSignupSmsSender implements PhoneAuthSmsSender {

    private final PhoneHttpSmsProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public void sendSignupOtp(String e164Digits, String otpCodeDigits) {
        String url = properties.getUrl().trim();
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException(
                    "sms-provider=http 인데 app.auth.phone.http.url (또는 PHONE_HTTP_SMS_URL) 가 비었습니다.");
        }

        String plus = KoreanMobileNormalizer.toPlusE164(e164Digits);
        String text =
                "[명상맵] 인증번호는 [" + otpCodeDigits + "] 입니다. 5분 이내 입력해 주세요.";

        ObjectNode body =
                objectMapper
                        .createObjectNode()
                        .put("e164Digits", e164Digits)
                        .put("e164International", plus)
                        .put("otpCode", otpCodeDigits)
                        .put("text", text);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());

        RestClient client =
                RestClient.builder()
                        .requestFactory(new BufferingClientHttpRequestFactory(factory))
                        .build();

        try {
            client.post()
                    .uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .acceptCharset(StandardCharsets.UTF_8)
                    .headers(h -> applyAuth(h))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("HTTP 문자 게이트웨이 호출 완료 destination(masked)={}", mask(plus));

        } catch (RestClientResponseException ex) {
            log.warn(
                    "HTTP 문자 게이트웨이 실패 status={} body={}",
                    ex.getStatusCode().value(),
                    truncate(ex.getResponseBodyAsString(), 500));
            throw new InfrastructureException(ErrorCode.EXTERNAL_SERVICE_ERROR, ex);
        } catch (RuntimeException ex) {
            throw new InfrastructureException(ErrorCode.EXTERNAL_SERVICE_ERROR, ex);
        }
    }

    private void applyAuth(HttpHeaders h) {
        String bearer = properties.getBearerToken();
        if (StringUtils.hasText(bearer)) {
            String v = bearer.trim();
            if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
                h.setBearerAuth(v.substring(7).trim());
            } else {
                h.setBearerAuth(v);
            }
        }
        h.add(HttpHeaders.USER_AGENT, "meditation-map-be/phone-http-sms");
    }

    private static String mask(String plusE164) {
        if (plusE164 == null || plusE164.length() < 6) {
            return "+****";
        }
        return plusE164.substring(0, plusE164.length() - 4) + "****";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
