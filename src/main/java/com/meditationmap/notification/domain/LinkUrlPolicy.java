package com.meditationmap.notification.domain;

/**
 * 알림 linkUrl 규칙 (계약 0절): FE 내부 경로만. {@code /} 로 시작, {@code //}·{@code http} 금지, 최대 300자.
 *
 * <p>외부 URL 을 허용하면 관리자 계정 하나로 전체 회원에게 피싱 링크를 보낼 수 있다.
 */
public final class LinkUrlPolicy {

    public static final int MAX_LENGTH = 300;

    private LinkUrlPolicy() {}

    public static boolean isAllowed(String linkUrl) {
        if (linkUrl == null) {
            return true;
        }
        if (linkUrl.isEmpty() || linkUrl.length() > MAX_LENGTH) {
            return false;
        }
        if (!linkUrl.startsWith("/") || linkUrl.startsWith("//")) {
            return false;
        }
        String lower = linkUrl.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("http:") || lower.contains("https:") || lower.contains("\\")) {
            return false;
        }
        for (int i = 0; i < linkUrl.length(); i++) {
            if (Character.isWhitespace(linkUrl.charAt(i)) || Character.isISOControl(linkUrl.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
