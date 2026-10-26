package com.meditationmap.centerchat.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 채팅 계약 C1·C3~C5 응답 모양. null 도 키는 내보낸다. S3 키·이메일(고객·오너 응답)은 어디에도 없다. */
public final class ChatViews {

    private ChatViews() {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PlaceRef(String id, String name, String thumbnailUrl) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProgramRef(String id, String title) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CustomerRef(String displayName) {}

    /** deleted(C9): 마지막 메시지가 삭제됐으면 true, preview null·imageCount 0. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LastMessage(String id, String senderRole, String preview, int imageCount, boolean deleted, String createdAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Thread(
            String id,
            PlaceRef place,
            ProgramRef program,
            CustomerRef customer,
            String status,
            LastMessage lastMessage,
            long unreadCount,
            boolean unanswered,
            String myLastReadMessageId,
            String counterpartLastReadMessageId,
            String createdAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Image(String url, String contentType, long sizeBytes) {}

    /**
     * deleted(C9): 삭제된 메시지는 body null·images [] 로 내보낸다(원문은 DB 에만, 관리자 신고 열람에서만 보인다).
     * deletable(C15): 지금 보는 회원이 보낸 본인이고 아직 삭제 전이면 true — DELETE 가 200 을 줄 조건과 같다. mine 은 "내 쪽(역할)"
     * 이라 오너 이관 뒤 새 오너에게 이전 오너 메시지도 mine:true 지만 deletable:false 다.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Message(
            String id,
            String threadId,
            String senderRole,
            boolean mine,
            String body,
            List<Image> images,
            String clientMessageId,
            boolean deleted,
            boolean deletable,
            String createdAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Page<T>(List<T> items, String nextCursor) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReadResult(String threadId, String lastReadMessageId, long unreadCount) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record UnreadCount(long unreadThreadCount, long unreadMessageCount) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Summary(String placeId, long unansweredCount, long unreadThreadCount, long unreadMessageCount) {}

    /**
     * C2 + C16. 기존 3키(placeId·available·reason) 유지, mode·kakaoType·kakaoUrl 추가. available = 우리 채팅으로 새 대화를 시작할 수
     * 있는가. reason: null | NO_OWNER | HIDDEN | KAKAO(우선순위 이 순서). kakaoType·kakaoUrl 은 reason == KAKAO 일 때만 값이 있다.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Availability(
            String placeId, boolean available, String reason, String mode, String kakaoType, String kakaoUrl) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Report(String id, String threadId, String reason, String detail, String status, String createdAt) {}

    // --- 관리자 ---

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminThreadRef(String id, String placeId, String placeName, String status) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminThreadDetailRef(
            String id, String placeId, String placeName, String status, ProgramRef program, String createdAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Reporter(String memberId, String email, String role) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminCustomer(String memberId, String email, String displayName) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MemberEmail(String memberId, String email) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StaffRef(String memberId, String loginId) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Resolution(String action, String note, StaffRef resolvedBy, String resolvedAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminReportSummary(
            String id, AdminThreadRef thread, Reporter reporter, String reason, String status, String createdAt, String resolvedAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminReportDetail(
            String id,
            AdminThreadDetailRef thread,
            Reporter reporter,
            AdminCustomer customer,
            MemberEmail currentOwner,
            String reason,
            String detail,
            String reportedUpToMessageId,
            String status,
            Resolution resolution,
            long otherReportCount,
            String createdAt) {}

    /** 관리자 열람: 원문 + 삭제 여부(C9 — 신고 증거 보존). */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminMessage(
            String id,
            String threadId,
            String senderRole,
            MemberEmail sender,
            String body,
            List<Image> images,
            boolean deleted,
            String deletedAt,
            String createdAt) {}
}
