package com.meditationmap.centerchat.application;

import com.meditationmap.centerchat.domain.ChatEnums.ReportReason;
import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.identity.domain.MemberRole;
import com.meditationmap.notification.application.NotificationCommandService;
import com.meditationmap.notification.application.NotificationDraft;
import com.meditationmap.notification.domain.NotificationRefType;
import com.meditationmap.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 채팅 알림 문구·링크(채팅 C7.2). 호출자 트랜잭션에서 저장하고 전달은 커밋 뒤. linkUrl 은 C8 라우트와 같아야 한다. */
@Component
@RequiredArgsConstructor
public class ChatNotifier {

    static final int PREVIEW = 50;

    private final NotificationCommandService notifications;
    private final MemberDirectory members;

    /**
     * 새 메시지 → 상대 쪽 알림을 <b>합쳐</b> 저장(같은 대화 미확인 1개 유지).
     *
     * @param unreadForRecipient 이 메시지를 포함한 받는 사람 기준 안 읽은 메시지 수(n≥2 면 제목에 표시)
     */
    void message(
            Long threadId, SenderRole sender, String recipientId, String placeName, String customerName,
            String body, int imageCount, long unreadForRecipient) {
        String preview = ChatAssembler.preview(body, PREVIEW);
        if (preview == null) {
            preview = imageCount > 0 ? "사진을 보냈어요" : "";
        }
        String name = placeName == null ? "" : placeName;
        NotificationDraft draft;
        if (sender == SenderRole.CUSTOMER) {
            draft =
                    new NotificationDraft(
                            NotificationType.CENTER_CHAT_MESSAGE,
                            unreadForRecipient >= 2 ? "새 문의 메시지 " + unreadForRecipient + "개" : "새 문의 메시지",
                            "[" + name + "] " + customerName + ": " + preview,
                            "/center/inquiries/" + threadId,
                            NotificationRefType.CENTER_CHAT,
                            String.valueOf(threadId));
        } else {
            draft =
                    new NotificationDraft(
                            NotificationType.CENTER_CHAT_MESSAGE,
                            "'" + name + "'에서 답장이 왔어요" + (unreadForRecipient >= 2 ? " (" + unreadForRecipient + ")" : ""),
                            preview,
                            "/profile/center-chats/" + threadId,
                            NotificationRefType.CENTER_CHAT,
                            String.valueOf(threadId));
        }
        notifications.replaceUnread(recipientId, draft);
    }

    void reported(Long reportId, String placeName, ReportReason reason) {
        notifications.notify(
                members.memberIdsWithRoles(MemberRole.ADMIN, MemberRole.EMPLOYEE),
                new NotificationDraft(
                        NotificationType.CENTER_CHAT_REPORTED,
                        "대화 신고가 접수되었어요",
                        "'" + (placeName == null ? "" : placeName) + "' — " + reason.korean(),
                        "/admin/chat-reports/" + reportId,
                        NotificationRefType.CENTER_CHAT_REPORT,
                        String.valueOf(reportId)));
    }

    void resolved(Long reportId, Long threadId, String reporterId, SenderRole reporterRole, String placeName) {
        notifications.notify(
                java.util.List.of(reporterId),
                new NotificationDraft(
                        NotificationType.CENTER_CHAT_REPORT_RESOLVED,
                        "신고가 처리되었어요",
                        "'" + (placeName == null ? "" : placeName) + "' 대화 신고를 검토했어요.",
                        (reporterRole == SenderRole.CUSTOMER ? "/profile/center-chats/" : "/center/inquiries/") + threadId,
                        NotificationRefType.CENTER_CHAT_REPORT,
                        String.valueOf(reportId)));
    }
}
