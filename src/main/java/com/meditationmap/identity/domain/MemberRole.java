package com.meditationmap.identity.domain;

/** 앱 일반 회원(MEMBER), 명상 전문가(EXPERT), 운영·개발·직원 스태프(ADMIN/DEV/EMPLOYEE) 구분 */
public enum MemberRole {
    MEMBER,
    EXPERT,
    ADMIN,
    DEV,
    EMPLOYEE;

    public static MemberRole fromDb(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEMBER;
        }
        String v = raw.trim().toUpperCase();
        if ("USER".equals(v)) {
            return MEMBER;
        }
        return MemberRole.valueOf(v);
    }

    public boolean isStaff() {
        return this == ADMIN || this == DEV || this == EMPLOYEE;
    }
}
