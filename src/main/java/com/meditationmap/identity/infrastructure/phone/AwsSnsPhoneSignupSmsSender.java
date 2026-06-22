package com.meditationmap.identity.infrastructure.phone;

import com.meditationmap.identity.application.port.out.PhoneAuthSmsSender;
import com.meditationmap.identity.application.support.KoreanMobileNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

@Slf4j
@RequiredArgsConstructor
public class AwsSnsPhoneSignupSmsSender implements PhoneAuthSmsSender {

    private final SnsClient snsClient;

    @Override
    public void sendSignupOtp(String e164Digits, String otpCodeDigits) {
        String destination = KoreanMobileNormalizer.toPlusE164(e164Digits);
        String message = "[명상맵] 인증번호는 [" + otpCodeDigits + "] 입니다. 5분 이내 입력해 주세요.";
        try {
            snsClient.publish(
                    PublishRequest.builder().phoneNumber(destination).message(message).build());
            log.debug("SMS published via SNS to {}", mask(destination));
        } catch (SnsException e) {
            log.error("SNS 문자 발송 실패 destination={}", mask(destination), e);
            throw e;
        }
    }

    private static String mask(String plusE164) {
        if (plusE164 == null || plusE164.length() < 6) {
            return "+****";
        }
        return plusE164.substring(0, plusE164.length() - 4) + "****";
    }
}
