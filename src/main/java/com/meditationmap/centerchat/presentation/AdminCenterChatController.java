package com.meditationmap.centerchat.presentation;

import com.meditationmap.centerchat.application.ChatAccess;
import com.meditationmap.centerchat.application.ChatAdminAccessLogger;
import com.meditationmap.centerchat.application.ChatMessageService;
import com.meditationmap.centerchat.application.ChatReportService;
import com.meditationmap.centerchat.application.ChatViews;
import com.meditationmap.centerchat.domain.ChatEnums.AccessKind;
import com.meditationmap.centerchat.presentation.dto.ChatRequests;
import com.meditationmap.identity.application.MemberDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 — 신고된 대화만(채팅 C5). 권한 DC3 = ADMIN·EMPLOYEE(DEV 403). 상세·대화·사진 열람마다 열람 로그(C5.5). 관리자는 원문과
 * 삭제 여부를 함께 본다(C9).
 */
@Slf4j
@Tag(name = "Admin Center Chat")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
public class AdminCenterChatController {

    private final MemberDirectory members;
    private final ChatReportService reports;
    private final ChatMessageService messages;
    private final ChatAccess access;
    private final ChatAdminAccessLogger accessLogger;

    @Operation(summary = "신고 목록 (FE GET /admin/center-chat-reports)")
    @GetMapping("/admin/center-chat-reports")
    public ChatViews.Page<ChatViews.AdminReportSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return reports.list(status, cursor, size);
    }

    @Operation(summary = "신고 상세 — 열람 로그 (FE GET /admin/center-chat-reports/{reportId})")
    @GetMapping("/admin/center-chat-reports/{reportId}")
    public ChatViews.AdminReportDetail detail(@AuthenticationPrincipal UserDetails user, @PathVariable String reportId) {
        ChatViews.AdminReportDetail d = reports.detail(reportId);
        log(Long.valueOf(d.thread().id()), user, AccessKind.REPORT);
        return d;
    }

    @Operation(summary = "신고 처리 — DISMISS | BLOCK_THREAD (FE POST /admin/center-chat-reports/{reportId}/resolve)")
    @PostMapping("/admin/center-chat-reports/{reportId}/resolve")
    public ChatViews.AdminReportDetail resolve(
            @AuthenticationPrincipal UserDetails user, @PathVariable String reportId, @RequestBody(required = false) ChatRequests.Resolve body) {
        return reports.resolve(
                members.requireMemberId(user.getUsername()), reportId, body == null ? null : body.action(), body == null ? null : body.note());
    }

    @Operation(summary = "신고된 대화 열람 — 원문·삭제 여부 (FE GET /admin/center-chats/{threadId}/messages)")
    @GetMapping("/admin/center-chats/{threadId}/messages")
    public ChatViews.Page<ChatViews.AdminMessage> messages(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String threadId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        Long id = access.loadForAdmin(threadId).getId();
        ChatViews.Page<ChatViews.AdminMessage> page = messages.adminList(id, cursor, size);
        log(id, user, AccessKind.MESSAGES);
        return page;
    }

    @Operation(summary = "신고된 대화 사진 (FE <img> /admin/center-chats/{t}/messages/{m}/images/{i})")
    @GetMapping("/admin/center-chats/{threadId}/messages/{messageId}/images/{index}")
    public ResponseEntity<byte[]> image(
            @AuthenticationPrincipal UserDetails user, @PathVariable String threadId, @PathVariable String messageId, @PathVariable String index) {
        Long id = access.loadForAdmin(threadId).getId();
        ResponseEntity<byte[]> res = ChatHttp.image(messages.adminImage(id, messageId, index));
        log(id, user, AccessKind.IMAGE);
        return res;
    }

    /** 열람 로그 실패는 열람을 막지 않는다(C5.5). */
    private void log(Long threadId, UserDetails user, AccessKind kind) {
        try {
            accessLogger.log(threadId, members.requireMemberId(user.getUsername()), kind);
        } catch (RuntimeException e) {
            log.warn("[CHAT] 열람 로그 실패 thread={}", threadId, e);
        }
    }
}
