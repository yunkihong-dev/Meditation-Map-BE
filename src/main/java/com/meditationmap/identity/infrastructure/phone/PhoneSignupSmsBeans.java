package com.meditationmap.identity.infrastructure.phone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.identity.application.port.out.PhoneAuthSmsSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    PhoneNaverSmsProperties.class
})
public class PhoneSignupSmsBeans {

    @Bean
    @ConditionalOnProperty(prefix = "app.auth.phone", name = "sms-provider", havingValue = "naver")
    public PhoneAuthSmsSender naverSensSignupSmsSender(
            PhoneNaverSmsProperties props, ObjectMapper objectMapper) {
        return new NaverSensPhoneSignupSmsSender(props, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(PhoneAuthSmsSender.class)
    public PhoneAuthSmsSender loggingSignupSmsFallback() {
        return new LoggingPhoneSignupSmsSender();
    }
}
