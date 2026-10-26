package com.meditationmap.notification.infrastructure.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.meditationmap.notification.application.port.out.PushSender;
import com.meditationmap.notification.infrastructure.config.NotificationProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 푸시 발송 구현 선택 — {@code PhoneSignupSmsBeans} 와 같은 패턴.
 *
 * <ul>
 *   <li>{@code app.notification.push.provider=fcm} → {@link FcmPushSender}. 이때 project-id·credentials-base64 가
 *       비어 있으면 <b>기동 실패</b>(계약 11절). 조용히 로그 모드로 내려가면 운영에서 푸시가 안 가는 걸 아무도 모른다
 *   <li>그 밖(기본 log) → {@link LoggingPushSender}. 키 없이 로컬·하네스 샌드박스에서 뜬다
 * </ul>
 */
@Slf4j
@Configuration
public class PushSenderBeans {

    static final String FIREBASE_APP_NAME = "meditation-map-push";

    @Bean
    @ConditionalOnProperty(prefix = "app.notification.push", name = "provider", havingValue = "fcm")
    public PushSender fcmPushSender(NotificationProperties properties) throws IOException {
        NotificationProperties.Fcm fcm = properties.fcm();
        List<String> missing = new ArrayList<>();
        if (fcm == null || fcm.projectId() == null || fcm.projectId().isBlank()) {
            missing.add("app.notification.fcm.project-id (FCM_PROJECT_ID)");
        }
        if (fcm == null || fcm.credentialsBase64() == null || fcm.credentialsBase64().isBlank()) {
            missing.add("app.notification.fcm.credentials-base64 (FCM_SERVICE_ACCOUNT_JSON_BASE64)");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "app.notification.push.provider=fcm 인데 다음 설정이 비어 있습니다: "
                            + String.join(", ", missing)
                            + ". 키 없이 띄우려면 PUSH_PROVIDER=log 로 두세요.");
        }
        byte[] json;
        try {
            json = Base64.getDecoder().decode(fcm.credentialsBase64().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "app.notification.fcm.credentials-base64 (FCM_SERVICE_ACCOUNT_JSON_BASE64) 가 base64 형식이 아닙니다.");
        }
        GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json));
        FirebaseOptions options =
                FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setProjectId(fcm.projectId().trim())
                        .build();
        FirebaseApp app =
                FirebaseApp.getApps().stream()
                        .filter(a -> FIREBASE_APP_NAME.equals(a.getName()))
                        .findFirst()
                        .orElseGet(() -> FirebaseApp.initializeApp(options, FIREBASE_APP_NAME));
        warnIfLocalWebBase(properties.webBaseUrl());
        return new FcmPushSender(app, properties.webBaseUrl());
    }

    /**
     * provider=fcm 인데 클릭 링크 base 가 localhost 면 WARN 한 줄. 기동 실패로 막지 않는다 — 로컬에서 fcm + localhost 로
     * 실발송을 시험해야 해서다(리더 결정, QA Q5). 운영은 application-prod.yml 기본값이 https://healingmedit.kr 이다.
     *
     * @return 경고했으면 true (테스트용)
     */
    static boolean warnIfLocalWebBase(String webBaseUrl) {
        String v = webBaseUrl == null ? "" : webBaseUrl.toLowerCase(java.util.Locale.ROOT);
        if (v.isBlank() || v.contains("://localhost") || v.contains("://127.0.0.1")) {
            log.warn(
                    "[PUSH:FCM] app.notification.web-base-url(NOTIFICATION_WEB_BASE_URL)={} — 푸시 알림 클릭이 로컬 주소로 열립니다. 운영이면 값을 확인하세요.",
                    webBaseUrl);
            return true;
        }
        return false;
    }

    @Bean
    @ConditionalOnMissingBean(PushSender.class)
    public PushSender loggingPushSender() {
        return new LoggingPushSender();
    }
}
