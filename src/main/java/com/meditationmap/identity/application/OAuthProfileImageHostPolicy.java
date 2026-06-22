package com.meditationmap.identity.application;

/** OAuth 제공자 CDN 등 공개 프로필 이미지 허용 호스트만 제한합니다. */
final class OAuthProfileImageHostPolicy {

    private OAuthProfileImageHostPolicy() {}

    static boolean isAllowed(String host) {
        if (host == null || host.isBlank()) return false;
        String h = host.trim().toLowerCase();
        if (h.endsWith(".")) return false;

        char first = h.charAt(0);
        if ((first >= '0' && first <= '9') || first == '[') {
            return false;
        }
        int dot = h.indexOf('.');
        if (dot < 0 || dot >= h.length() - 1) {
            return false;
        }

        return h.endsWith(".kakaocdn.net")
                || h.endsWith(".googleusercontent.com")
                || h.endsWith(".pstatic.net");
    }
}
