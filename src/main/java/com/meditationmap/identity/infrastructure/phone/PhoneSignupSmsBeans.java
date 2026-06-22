package com.meditationmap.identity.infrastructure.phone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.identity.application.port.out.PhoneAuthSmsSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
@EnableConfigurationProperties({
    PhoneHttpSmsProperties.class,
    PhoneNaverSmsProperties.class,
    PhoneChannelTalkProperties.class
})
public class PhoneSignupSmsBeans {

    @Bean
    @ConditionalOnProperty(prefix = "app.auth.phone", name = "sms-provider", havingValue = "sns")
    @ConditionalOnBean(SnsClient.class)
    public PhoneAuthSmsSender awsSnsSignupSmsSender(SnsClient snsClient) {
        return new AwsSnsPhoneSignupSmsSender(snsClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.auth.phone", name = "sms-provider", havingValue = "http")
    public PhoneAuthSmsSender httpWebhookSignupSmsSender(
            PhoneHttpSmsProperties props, ObjectMapper objectMapper) {
        return new HttpWebhookPhoneSignupSmsSender(props, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.auth.phone", name = "sms-provider", havingValue = "naver")
    public PhoneAuthSmsSender naverSensSignupSmsSender(
            PhoneNaverSmsProperties props, ObjectMapper objectMapper) {
        return new NaverSensPhoneSignupSmsSender(props, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.auth.phone", name = "sms-provider", havingValue = "channeltalk")
    public PhoneAuthSmsSender channelTalkSignupOtpSender(
            PhoneChannelTalkProperties props, ObjectMapper objectMapper) {
        return new ChannelTalkPhoneSignupOtpSender(props, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(PhoneAuthSmsSender.class)
    public PhoneAuthSmsSender loggingSignupSmsFallback() {
        return new LoggingPhoneSignupSmsSender();
    }
}
