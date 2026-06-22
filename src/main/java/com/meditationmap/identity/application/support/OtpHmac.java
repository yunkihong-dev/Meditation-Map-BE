package com.meditationmap.identity.application.support;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class OtpHmac {

    private OtpHmac() {}

    public static String sign(String secret, String phoneDigits, String code) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "OTP HMAC secret 이 비었습니다 (JWT_SECRET / PHONE_OTP_HMAC_SECRET 확인).");
        }
        String payload = phoneDigits + "|" + code;
        Mac mac = mac(secret);
        return bytesToHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int cmp = 0;
        for (int i = 0; i < a.length(); i++) {
            cmp |= a.charAt(i) ^ b.charAt(i);
        }
        return cmp == 0;
    }

    private static Mac mac(String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC 초기화 실패", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
