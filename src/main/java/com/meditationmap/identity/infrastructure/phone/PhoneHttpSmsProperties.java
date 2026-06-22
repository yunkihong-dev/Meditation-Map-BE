package com.meditationmap.identity.infrastructure.phone;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * sms-provider=http 일 때 문자를 외부(사설 포함) 게이트웨이 HTTP 엔드포인트로 POST 합니다.
 * 실제 알리고·SENS·CoolSMS 등은 별도 중계 서버에서 처리합니다.
 */
@Data
@ConfigurationProperties(prefix = "app.auth.phone.http")
public class PhoneHttpSmsProperties {

    /** POST 받을 주소 예: {@code http://192.168.0.50:9080/internal/sms/otp} */
    private String url = "";

    /** 있으면 {@code Authorization: Bearer &lt;값&gt;} */
    private String bearerToken = "";

    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 15_000;
}
