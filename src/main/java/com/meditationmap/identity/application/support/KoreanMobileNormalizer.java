package com.meditationmap.identity.application.support;

import com.meditationmap.identity.domain.InvalidPhoneNumberException;
import java.util.regex.Pattern;

/** 대한민국 휴대전화 → 국번 없이 저장용 E.164 형태 숫자만 (예: 821012345678). */
public final class KoreanMobileNormalizer {

    private static final Pattern STRICT = Pattern.compile("^8210\\d{8}$");

    private KoreanMobileNormalizer() {}

    /**
     * @return Redis·DB 에 쓸 숫자만 문자열 (+ 제외). 예: {@code 821012345678}
     */
    public static String normalizeToE164Digits(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidPhoneNumberException();
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new InvalidPhoneNumberException();
        }

        String e164digits;
        if (digits.startsWith("82")) {
            e164digits = digits;
        } else if (digits.startsWith("010") && digits.length() == 11) {
            e164digits = "82" + digits.substring(1);
        } else if (digits.length() == 10 && digits.startsWith("10")) {
            // 선행 0 생략: 1012345678
            e164digits = "82" + digits;
        } else {
            throw new InvalidPhoneNumberException();
        }

        if (!STRICT.matcher(e164digits).matches()) {
            throw new InvalidPhoneNumberException();
        }
        return e164digits;
    }

    /** SMS 발송용 국제 표기 (+8210...) */
    public static String toPlusE164(String e164digits) {
        return "+" + e164digits;
    }
}
