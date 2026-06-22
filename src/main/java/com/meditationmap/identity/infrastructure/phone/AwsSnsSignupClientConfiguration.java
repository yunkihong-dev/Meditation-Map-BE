package com.meditationmap.identity.infrastructure.phone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
@ConditionalOnProperty(prefix = "app.auth.phone", name = "sms-provider", havingValue = "sns")
public class AwsSnsSignupClientConfiguration {

    @Bean
    public SnsClient signupSnsClient(
            @Value("${app.auth.phone.sns.region:ap-northeast-2}") String regionId) {
        return SnsClient.builder().region(Region.of(regionId)).build();
    }
}
