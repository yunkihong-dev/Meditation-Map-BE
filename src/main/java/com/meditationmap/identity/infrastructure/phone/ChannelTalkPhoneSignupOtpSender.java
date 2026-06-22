package com.meditationmap.identity.infrastructure.phone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meditationmap.identity.application.port.out.PhoneAuthSmsSender;
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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 채널톡 Open API: 회원(memberId=휴대전화 E.164 숫자) upsert → UserChat 확보 → 봇 메시지로 OTP 전달.
 *
 * <p>휴대폰 SMS 가 아니라 채널톡 상담/메신저 쪽으로 코드가 갑니다. 프로필 {@code mobileNumber} 는 채널톡 고객
 * 식별·팔로업 문자 등에 쓰입니다.
 */
@Slf4j
@RequiredArgsConstructor
public class ChannelTalkPhoneSignupOtpSender implements PhoneAuthSmsSender {

    private static final MediaType JSON_UTF8 =
            new MediaType("application", "json", StandardCharsets.UTF_8);

    private final PhoneChannelTalkProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public void sendSignupOtp(String e164Digits, String otpCodeDigits) {
        validateConfig();

        String memberId = e164Digits;
        String domesticMobile = e164ToDomestic(e164Digits);
        String text =
                "[명상맵] 인증번호는 [" + otpCodeDigits + "] 입니다. 5분 이내 입력해 주세요.";

        RestClient client = buildClient();

        String userId = upsertUser(client, memberId, domesticMobile);
        String userChatId = resolveUserChatId(client, userId);
        postOtpMessage(client, userChatId, text);

        log.debug(
                "채널톡 UserChat OTP 전송 완료 memberId(masked)={}",
                maskMemberId(memberId));
    }

    private void validateConfig() {
        if (!StringUtils.hasText(props.getAccessKey())) {
            throw new IllegalStateException(
                    "sms-provider=channeltalk 인데 CHANNEL_TALK_ACCESS_KEY 가 비었습니다.");
        }
        if (!StringUtils.hasText(props.getAccessSecret())) {
            throw new IllegalStateException(
                    "sms-provider=channeltalk 인데 CHANNEL_TALK_ACCESS_SECRET 이 비었습니다.");
        }
        if (!StringUtils.hasText(props.getBotName())) {
            throw new IllegalStateException(
                    "sms-provider=channeltalk 인데 CHANNEL_TALK_BOT_NAME(봇 이름) 이 비었습니다.");
        }
    }

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());

        return RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(factory))
                .defaultHeader("x-access-key", props.getAccessKey().trim())
                .defaultHeader("x-access-secret", props.getAccessSecret().trim())
                .defaultHeader(HttpHeaders.USER_AGENT, "meditation-map-be/channeltalk")
                .build();
    }

    private String upsertUser(RestClient client, String memberId, String domesticMobile) {
        ObjectNode profile = objectMapper.createObjectNode();
        profile.put("mobileNumber", domesticMobile);
        profile.put("name", "회원가입 인증");

        ObjectNode body = objectMapper.createObjectNode();
        body.set("profile", profile);

        String path = "/open/v5/users/@" + memberId;
        JsonNode response = exchangeJson(client, "PUT", URI.create(apiBase() + path), body);
        String userId = readRequiredText(response, "user", "id");
        if (!StringUtils.hasText(userId)) {
            throw new InfrastructureException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    new IllegalStateException("채널톡 user upsert 응답에 user.id 가 없습니다."));
        }
        return userId;
    }

    private String resolveUserChatId(RestClient client, String userId) {
        String listPath = "/open/v5/users/" + userId + "/user-chats";
        URI listUri =
                UriComponentsBuilder.fromUriString(apiBase() + listPath)
                        .queryParam("limit", 1)
                        .queryParam("sortOrder", "desc")
                        .build(true)
                        .toUri();

        JsonNode listResponse = exchangeJson(client, "GET", listUri, null);
        JsonNode chats = listResponse.get("userChats");
        if (chats != null && chats.isArray() && !chats.isEmpty()) {
            String existing = chats.get(0).path("id").asText(null);
            if (StringUtils.hasText(existing)) {
                return existing;
            }
        }

        String createPath = "/open/v5/users/" + userId + "/user-chats";
        JsonNode created =
                exchangeJson(client, "POST", URI.create(apiBase() + createPath), objectMapper.createObjectNode());
        String userChatId = readRequiredText(created, "userChat", "id");
        if (!StringUtils.hasText(userChatId)) {
            throw new InfrastructureException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    new IllegalStateException("채널톡 userChat 생성 응답에 userChat.id 가 없습니다."));
        }
        return userChatId;
    }

    private void postOtpMessage(RestClient client, String userChatId, String text) {
        ArrayNode blocks = objectMapper.createArrayNode();
        blocks.add(
                objectMapper
                        .createObjectNode()
                        .put("type", "text")
                        .put("value", text));

        ObjectNode body =
                objectMapper.createObjectNode().put("plainText", text).set("blocks", blocks);

        URI uri =
                UriComponentsBuilder.fromUriString(
                                apiBase() + "/open/v5/user-chats/" + userChatId + "/messages")
                        .queryParam("botName", props.getBotName().trim())
                        .build(true)
                        .toUri();

        exchangeJson(client, "POST", uri, body);
    }

    private JsonNode exchangeJson(RestClient client, String method, URI uri, ObjectNode body) {
        try {
            RestClient.RequestBodySpec spec =
                    client.method(org.springframework.http.HttpMethod.valueOf(method))
                            .uri(uri)
                            .accept(JSON_UTF8)
                            .contentType(JSON_UTF8);

            JsonNode response =
                    (body != null ? spec.body(body) : spec)
                            .retrieve()
                            .body(JsonNode.class);
            return response != null ? response : objectMapper.createObjectNode();
        } catch (RestClientResponseException ex) {
            log.warn(
                    "채널톡 API 실패 method={} uri={} status={} body={}",
                    method,
                    uri,
                    ex.getStatusCode().value(),
                    truncate(ex.getResponseBodyAsString(), 500));
            throw new InfrastructureException(ErrorCode.EXTERNAL_SERVICE_ERROR, ex);
        } catch (RuntimeException ex) {
            throw new InfrastructureException(ErrorCode.EXTERNAL_SERVICE_ERROR, ex);
        }
    }

    private String apiBase() {
        String base = props.getBaseUrl().trim();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String e164ToDomestic(String e164Digits) {
        if (e164Digits == null || !e164Digits.startsWith("82")) {
            throw new IllegalArgumentException("e164 한국 휴대전화 형식이 아닙니다.");
        }
        return "0" + e164Digits.substring(2);
    }

    private static String readRequiredText(JsonNode root, String objectField, String idField) {
        if (root == null) {
            return null;
        }
        JsonNode node = root.path(objectField).path(idField);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private static String maskMemberId(String memberId) {
        if (memberId == null || memberId.length() < 6) {
            return "****";
        }
        return memberId.substring(0, memberId.length() - 4) + "****";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
