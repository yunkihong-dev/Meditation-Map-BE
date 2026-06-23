package com.meditationmap.identity.application.port.out;

/** 회원가입 등 휴대전화 문자(OTP) 발송 포트 — 실구현은 SNS, 로컬은 로깅 어댑터 */
public interface PhoneAuthSmsSender {

    void sendSignupOtp(String e164Digits, String otpCodeDigits);
}
