package com.meditationmap.centerchat.presentation;

import com.meditationmap.centerchat.application.ChatAccess.Viewer;
import com.meditationmap.centerchat.application.ChatAssembler;
import com.meditationmap.centerchat.application.ChatAccess;
import com.meditationmap.centerchat.application.ChatInboxService;
import com.meditationmap.centerchat.application.ChatMessageService;
import com.meditationmap.centerchat.application.ChatReportService;
import com.meditationmap.centerchat.application.ChatViews;
import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.centerchat.presentation.dto.ChatRequests;
import com.meditationmap.identity.application.MemberDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 오너 문의함(채팅 계약 C4). 권한 = 회원 + 지금 그 장소의 오너(DC2). 실패는 C0 대로 404. */
@Tag(name = "Center Chat (owner inbox)")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class OwnerCenterInboxController {

    private final MemberDirectory members;
    private final ChatInboxService inbox;
    private final ChatMessageService messages;
    private final ChatReportService reportService;
    private final ChatAccess access;
    private final ChatAssembler assembler;

    private Viewer me(UserDetails user) {
        return new Viewer(members.requireMemberId(user.getUsername()), SenderRole.OWNER);
    }

    @Operation(summary = "문의함 목록 — placeId·unanswered 필터 (FE GET /me/center-inbox)")
    @GetMapping("/me/center-inbox")
    public ChatViews.Page<ChatViews.Thread> list(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(required = false) String placeId,
            @RequestParam(required = false) String unanswered,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return inbox.ownerList(me(user).memberId(), placeId, unanswered, cursor, size);
    }

    @Operation(summary = "미답변·안 읽은 수 — 대시보드 '신규 문의' 카드 = unansweredCount (FE GET /me/center-inbox/summary)")
    @GetMapping("/me/center-inbox/summary")
    public ChatViews.Summary summary(@AuthenticationPrincipal UserDetails user, @RequestParam(required = false) String placeId) {
        return inbox.summary(me(user).memberId(), placeId);
    }

    @Operation(summary = "대화 1건 (FE GET /me/center-inbox/{threadId})")
    @GetMapping("/me/center-inbox/{threadId}")
    public ChatViews.Thread get(@AuthenticationPrincipal UserDetails user, @PathVariable String threadId) {
        Viewer v = me(user);
        return assembler.thread(v, access.load(v, threadId));
    }

    @Operation(summary = "메시지 — cursor(과거) 또는 after(따라잡기) (FE GET /me/center-inbox/{threadId}/messages)")
    @GetMapping("/me/center-inbox/{threadId}/messages")
    public ChatViews.Page<ChatViews.Message> messages(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String threadId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String after) {
        return messages.list(me(user), threadId, cursor, size, after);
    }

    @Operation(summary = "전송 — multipart, clientMessageId 멱등 (FE POST /me/center-inbox/{threadId}/messages)")
    @PostMapping(value = "/me/center-inbox/{threadId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatViews.Message> send(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable String threadId,
            @RequestParam(value = "clientMessageId", required = false) String clientMessageId,
            @RequestParam(value = "body", required = false) String body,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        ChatMessageService.SendResult r = messages.send(me(user), threadId, clientMessageId, body, ChatHttp.images(images));
        return ChatHttp.created(r.created(), r.message());
    }

    @Operation(summary = "내 메시지 삭제(소프트, 멱등) (FE DELETE /me/center-inbox/{threadId}/messages/{messageId})")
    @DeleteMapping("/me/center-inbox/{threadId}/messages/{messageId}")
    public ChatViews.Message delete(
            @AuthenticationPrincipal UserDetails user, @PathVariable String threadId, @PathVariable String messageId) {
        return messages.delete(me(user), threadId, messageId);
    }

    @Operation(summary = "읽음 — 역행 없음 (FE POST /me/center-inbox/{threadId}/read)")
    @PostMapping("/me/center-inbox/{threadId}/read")
    public ChatViews.ReadResult read(
            @AuthenticationPrincipal UserDetails user, @PathVariable String threadId, @RequestBody(required = false) ChatRequests.Read body) {
        return messages.read(me(user), threadId, body == null ? null : body.lastReadMessageId());
    }

    @Operation(summary = "사진 (FE <img> /me/center-inbox/{t}/messages/{m}/images/{i})")
    @GetMapping("/me/center-inbox/{threadId}/messages/{messageId}/images/{index}")
    public ResponseEntity<byte[]> image(
            @AuthenticationPrincipal UserDetails user, @PathVariable String threadId, @PathVariable String messageId, @PathVariable String index) {
        return ChatHttp.image(messages.image(me(user), threadId, messageId, index));
    }

    @Operation(summary = "신고 (FE POST /me/center-inbox/{threadId}/reports)")
    @PostMapping("/me/center-inbox/{threadId}/reports")
    public ResponseEntity<ChatViews.Report> report(
            @AuthenticationPrincipal UserDetails user, @PathVariable String threadId, @RequestBody(required = false) ChatRequests.Report body) {
        return ChatHttp.created(
                true, reportService.create(me(user), threadId, body == null ? null : body.reason(), body == null ? null : body.detail()));
    }
}
