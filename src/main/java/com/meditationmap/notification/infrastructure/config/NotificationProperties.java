package com.meditationmap.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.notification.*} (계약 11절). 시크릿(FCM 서비스 계정)은 env 로만 주입한다.
 *
 * @param webBaseUrl FCM 알림 클릭 시 열 웹 주소의 base (env NOTIFICATION_WEB_BASE_URL)
 */
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        @DefaultValue("http://localhost:3000") String webBaseUrl,
        @DefaultValue Sse sse,
        @DefaultValue Push push,
        @DefaultValue Fcm fcm) {

    public record Sse(
            @DefaultValue("1800000") long timeoutMs,
            @DefaultValue("25000") long heartbeatMs,
            @DefaultValue("5") int maxConnectionsPerMember) {}

    /** @param provider {@code log}(기본) | {@code fcm} */
    public record Push(@DefaultValue("log") String provider) {}

    /** @param credentialsBase64 서비스 계정 JSON 을 base64 로 인코딩한 값. 시크릿 — 로그에 찍지 않는다 */
    public record Fcm(String projectId, String credentialsBase64) {
        @Override
        public String toString() {
            return "Fcm[projectId=" + projectId + ", credentialsBase64=" + (credentialsBase64 == null || credentialsBase64.isBlank() ? "<empty>" : "<set>") + "]";
        }
    }
}
