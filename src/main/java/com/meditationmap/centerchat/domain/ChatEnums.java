package com.meditationmap.centerchat.domain;

/** 채팅 도메인 값(계약 C1·C6). DB 에는 이름 문자열로 저장한다(@Enumerated 미사용 — CHECK 제약 부작용 회피, 계획 10.2). */
public final class ChatEnums {

    private ChatEnums() {}

    public enum ThreadStatus { OPEN, BLOCKED }

    public enum SenderRole {
        CUSTOMER, OWNER;

        public SenderRole other() {
            return this == CUSTOMER ? OWNER : CUSTOMER;
        }
    }

    public enum ReportStatus { PENDING, RESOLVED, DISMISSED }

    public enum ReportReason {
        SPAM("스팸"), ABUSE("욕설·괴롭힘"), FRAUD("사기 의심"), INAPPROPRIATE_IMAGE("부적절한 사진"), PRIVACY("개인정보 노출"), OTHER("기타");

        private final String korean;

        ReportReason(String korean) {
            this.korean = korean;
        }

        public String korean() {
            return korean;
        }
    }

    public enum ReportAction { DISMISS, BLOCK_THREAD }

    public enum AccessKind { REPORT, MESSAGES, IMAGE }

    /** 열린 대화 유니크 키(계획 10.3) — 프로그램이 없으면 "-" 라서 항상 NOT NULL. */
    public static String openKey(String placeId, String customerId, String programId) {
        return placeId + "|" + customerId + "|" + (programId == null ? "-" : programId);
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
