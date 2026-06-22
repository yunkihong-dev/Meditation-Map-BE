package com.meditationmap.identity.infrastructure.phone;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * sms-provider=channeltalk 일 때 채널톡 Open API 로 UserChat 에 OTP 를 보냅니다.
 *
 * <p>채널 설정 → 보안 및 개발 → API 에서 Access Key / Secret 발급, 메시지 발송용 봇 이름이 필요합니다.
 */
@Data
@ConfigurationProperties(prefix = "app.auth.phone.channeltalk")
public class PhoneChannelTalkProperties {

    private String accessKey = "";
    private String accessSecret = "";
    /** UserChat 메시지 POST 시 {@code botName} 쿼리 — 워크스pace 에 등록된 봇 */
    private String botName = "";

    private String baseUrl = "https://api.channel.io";

    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 15_000;
}
