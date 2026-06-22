package com.meditationmap.identity.application;

import com.meditationmap.identity.application.port.out.PhoneAuthSmsSender;
import com.meditationmap.identity.application.support.KoreanMobileNormalizer;
import com.meditationmap.identity.application.support.OtpHmac;
import com.meditationmap.identity.domain.MemberRepository;
import com.meditationmap.identity.domain.PhoneAlreadyRegisteredException;
import com.meditationmap.identity.domain.PhoneOtpInvalidException;
import com.meditationmap.identity.domain.PhoneOtpRateLimitedException;
import com.meditationmap.identity.domain.PhoneVerificationTokenInvalidException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneSignupVerificationApplicationService {

    private static final String OTP_KEY = "auth:signup:otp:";
    private static final String COOLDOWN_KEY = "auth:signup:otp:cd:";
    private static final String PHONE_VERIFIED_KEY = "auth:signup:phoneok:";

    private final StringRedisTemplate stringRedisTemplate;
    private final PhoneAuthSmsSender phoneAuthSmsSender;
    private final MemberRepository memberRepository;

    /** 비어 있으면 {@link #jwtSecretForOtpFallback} 으로 대체합니다(빈 env 가 JWT 대체를 막는 경우 방지). */
    @Value("${app.auth.phone.otp-hmac-secret:}")
    private String otpHmacSecretConfigured;

    @Value("${app.jwt.secret:}")
    private String jwtSecretForOtpFallback;

    /** {@link PostConstruct} 에서 채워집니다. */
    private String otpHmacSecret;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.phone.otp-ttl-seconds:300}")
    private long otpTtlSeconds;

    @Value("${app.auth.phone.verify-token-ttl-seconds:900}")
    private long verifyTokenTtlSeconds;

    @Value("${app.auth.phone.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    @PostConstruct
    void resolveOtpHmacSecret() {
        String fromPhone =
                otpHmacSecretConfigured != null ? otpHmacSecretConfigured.trim() : "";
        String fromJwt =
                jwtSecretForOtpFallback != null ? jwtSecretForOtpFallback.trim() : "";
        if (!StringUtils.hasText(fromPhone)) {
            otpHmacSecret = fromJwt;
        } else {
            otpHmacSecret = fromPhone;
        }
        if (!StringUtils.hasText(otpHmacSecret)) {
            throw new IllegalStateException(
                    "전화 인증번호(HMAC) 키가 비었습니다. .env 또는 환경 변수에 JWT_SECRET 또는"
                            + " PHONE_OTP_HMAC_SECRET 을 채워 주세요. (PHONE_OTP_HMAC_SECRET 빈 문자열이면 JWT 로"
                            + " 대체되지 않는 환경이 있어 두 값 모두 비면 실패합니다.)");
        }
    }

    /** 1) 문자 요청 — 매번 새 6자리 코드 생성 후 SMS(또는 log) 발송 */
    public void requestSignupPhoneOtp(String rawPhoneInput) {
        String phone = KoreanMobileNormalizer.normalizeToE164Digits(rawPhoneInput);
        if (memberRepository.existsByPhoneE164(phone)) {
            throw new PhoneAlreadyRegisteredException();
        }

        Duration cooldown = Duration.ofSeconds(Math.max(10, resendCooldownSeconds));
        Boolean cool =
                Boolean.TRUE.equals(
                        stringRedisTemplate
                                .opsForValue()
                                .setIfAbsent(COOLDOWN_KEY + phone, "1", cooldown));
        if (!cool) {
            throw new PhoneOtpRateLimitedException();
        }

        String otp = nextSixDigitCode();
        String signature = OtpHmac.sign(otpHmacSecret, phone, otp);
        Duration otpTtl = Duration.ofSeconds(Math.max(60, otpTtlSeconds));

        stringRedisTemplate.opsForValue().set(OTP_KEY + phone, signature, otpTtl);

        try {
            phoneAuthSmsSender.sendSignupOtp(phone, otp);
        } catch (RuntimeException ex) {
            stringRedisTemplate.delete(COOLDOWN_KEY + phone);
            stringRedisTemplate.delete(OTP_KEY + phone);
            throw ex;
        }
    }

    /** 2) 코드 검증 — 성공 시 회원가입에 넘길 일회성 토큰(UUID 문자열) */
    public String confirmSignupPhoneOtp(String rawPhoneInput, String codeDigits) {
        String phone = KoreanMobileNormalizer.normalizeToE164Digits(rawPhoneInput);

        String stored = stringRedisTemplate.opsForValue().get(OTP_KEY + phone);
        if (stored == null) {
            throw new PhoneOtpInvalidException();
        }

        String attempt = OtpHmac.sign(otpHmacSecret, phone, codeDigits.trim());
        if (!OtpHmac.constantTimeEquals(stored, attempt)) {
            throw new PhoneOtpInvalidException();
        }

        stringRedisTemplate.delete(OTP_KEY + phone);

        UUID tokenId = UUID.randomUUID();
        String token = tokenId.toString();
        Duration ttl = Duration.ofSeconds(Math.max(300, verifyTokenTtlSeconds));
        stringRedisTemplate.opsForValue().set(PHONE_VERIFIED_KEY + token, phone, ttl);

        log.debug("휴대전화 회원가입 인증 성공 masking={}", maskPhone(phone));
        return token;
    }

    /**
     * 3) 가입 처리 시 1회 사용 — 즉시 Redis 에서 삭제합니다.
     *
     * @return DB 저장용 E.164 숫자열
     */
    public String consumePhoneVerificationTokenOnce(String opaqueToken) {
        if (opaqueToken == null || opaqueToken.isBlank()) {
            throw new PhoneVerificationTokenInvalidException();
        }
        UUID parsed;
        try {
            parsed = UUID.fromString(opaqueToken.trim());
        } catch (IllegalArgumentException e) {
            throw new PhoneVerificationTokenInvalidException();
        }

        String key = PHONE_VERIFIED_KEY + parsed;
        String phone = stringRedisTemplate.opsForValue().get(key);
        if (phone == null) {
            throw new PhoneVerificationTokenInvalidException();
        }
        stringRedisTemplate.delete(key);
        return phone;
    }

    private String nextSixDigitCode() {
        int n = 100_000 + secureRandom.nextInt(900_000);
        return String.format("%06d", n);
    }

    private static String maskPhone(String e164digits) {
        if (e164digits == null || e164digits.length() < 5) {
            return "****";
        }
        return e164digits.substring(0, e164digits.length() - 4) + "****";
    }
}
