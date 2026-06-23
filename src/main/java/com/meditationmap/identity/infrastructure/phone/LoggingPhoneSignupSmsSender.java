package com.meditationmap.identity.infrastructure.phone;

import com.meditationmap.identity.application.port.out.PhoneAuthSmsSender;
import com.meditationmap.identity.application.support.KoreanMobileNormalizer;
import lombok.extern.slf4j.Slf4j;

/**
 * sms-provider=log 일 때 동작합니다. 실제 문자 대신 매 요청마다 <strong>새로 생성된 OTP</strong>를 로그에만 출력합니다 (고정값
 * 우회 금지). 로컬·스테이징에서 문자 대신 로그를 확인할 때만 사용하세요.
 */
@Slf4j
public class LoggingPhoneSignupSmsSender implements PhoneAuthSmsSender {

    @Override
    public void sendSignupOtp(String e164Digits, String otpCodeDigits) {
        String plus = KoreanMobileNormalizer.toPlusE164(e164Digits);
        String masked = maskE164digits(e164Digits);
        log.info(
                "[SMS:LOG] 회원가입 인증번호 발송 시뮬레이션 to={} masked={} code={} (운영에서는 sms-provider=naver 사용)",
                plus,
                masked,
                otpCodeDigits);
    }

    /** 마지막 4자리만 노출 */
    private static String maskE164digits(String e164digits) {
        if (e164digits == null || e164digits.length() < 5) {
            return "****";
        }
        return e164digits.substring(0, e164digits.length() - 4) + "****";
    }
}
