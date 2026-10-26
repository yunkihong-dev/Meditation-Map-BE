package com.meditationmap.notification.infrastructure.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.meditationmap.notification.application.port.out.PushSender;
import com.meditationmap.notification.infrastructure.config.NotificationProperties;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * 푸시 구현 선택과 테스트 격리 (계약 11절, QA Q5).
 *
 * <p>보장: provider=log(기본)면 LoggingPushSender · 테스트 classpath 가 provider=log 로 고정돼 있어 개발자 .env 의
 * PUSH_PROVIDER=fcm 이 테스트에서 실 FCM 을 부르지 않는다 · fcm+localhost 는 기동을 막지 않고 경고만 한다.
 */
class PushSenderBeansTest {

    @Configuration
    @EnableConfigurationProperties(NotificationProperties.class)
    static class Props {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(Props.class, PushSenderBeans.class);

    @Test
    @DisplayName("provider 미지정·log 면 LoggingPushSender")
    void 기본은_로그() {
        runner.run(ctx -> assertThat(ctx.getBean(PushSender.class)).isInstanceOf(LoggingPushSender.class));
        runner.withPropertyValues("app.notification.push.provider=log")
                .run(ctx -> assertThat(ctx.getBean(PushSender.class)).isInstanceOf(LoggingPushSender.class));
    }

    @Test
    @DisplayName("테스트 classpath 는 provider=log 로 고정돼 있다 — .env 의 fcm 키로 실발송하지 않는다")
    void 테스트_프로파일은_log_고정() throws Exception {
        Properties p = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        assertThat(p.getProperty("app.notification.push.provider")).isEqualTo("log");
        assertThat(p.getProperty("app.notification.fcm.credentials-base64")).isEmpty();
    }

    @Test
    @DisplayName("fcm 인데 키가 비면 기동 실패하고 메시지에 빈 키 이름이 있다 (계약 11절)")
    void fcm_키_없음() {
        runner.withPropertyValues("app.notification.push.provider=fcm")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause()
                            .hasMessageContaining("FCM_PROJECT_ID")
                            .hasMessageContaining("FCM_SERVICE_ACCOUNT_JSON_BASE64");
                });
    }

    @Test
    @DisplayName("web-base-url 이 localhost 면 경고(기동은 막지 않음), 운영 도메인이면 조용")
    void localhost_경고() {
        assertThat(PushSenderBeans.warnIfLocalWebBase("http://localhost:3000")).isTrue();
        assertThat(PushSenderBeans.warnIfLocalWebBase("http://127.0.0.1:3000")).isTrue();
        assertThat(PushSenderBeans.warnIfLocalWebBase("https://healingmedit.kr")).isFalse();
    }
}
