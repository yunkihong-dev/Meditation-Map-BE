package com.meditationmap.centerchat.application;

import com.meditationmap.centerchat.application.ChatAccess.Viewer;
import com.meditationmap.centerchat.domain.ChatEnums;
import com.meditationmap.centerchat.domain.ChatEnums.ReportAction;
import com.meditationmap.centerchat.domain.ChatEnums.ReportReason;
import com.meditationmap.centerchat.domain.ChatEnums.ReportStatus;
import com.meditationmap.centerchat.domain.ChatEnums.SenderRole;
import com.meditationmap.centerchat.domain.ChatEnums.ThreadStatus;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatReportJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatReportSpringDataRepository;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatThreadSpringDataRepository;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.shared.domain.ApiTimestamps;
import com.meditationmap.shared.exception.DetailedDomainException;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신고 생성(C3.9)·관리자 목록·상세·처리(C5.1~C5.3). */
@Service
@RequiredArgsConstructor
public class ChatReportService {

    private final ChatAccess access;
    private final CenterChatReportSpringDataRepository reports;
    private final CenterChatThreadSpringDataRepository threads;
    private final ChatPlaceLookup places;
    private final PlaceOwnership ownership;
    private final MemberDirectory members;
    private final ChatNotifier notifier;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public ChatViews.Report create(Viewer viewer, String threadIdRaw, String reasonRaw, String detailRaw) {
        CenterChatThreadJpaEntity t = access.lock(viewer, threadIdRaw);
        ReportReason reason = ChatEnums.parse(ReportReason.class, reasonRaw);
        String detail = detailRaw == null || detailRaw.isBlank() ? null : detailRaw.trim();
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        if (reason == null) {
            errors.put("reason", "SPAM·ABUSE·FRAUD·INAPPROPRIATE_IMAGE·PRIVACY·OTHER 중 하나여야 합니다.");
        }
        if (detail != null && detail.length() > 1000) {
            errors.put("detail", "1000자 이하여야 합니다.");
        }
        if (reason == ReportReason.OTHER && detail == null) {
            errors.put("detail", "기타 사유는 내용을 적어 주세요.");
        }
        if (t.getLastMessageId() == null) {
            errors.put("threadId", "메시지가 없는 대화는 신고할 수 없습니다.");
        }
        if (!errors.isEmpty()) {
            throw DetailedDomainException.fields(ErrorCode.VALIDATION_FAILED, errors);
        }
        String key = t.getId() + ":" + viewer.memberId();
        if (reports.findByActiveKey(key).isPresent()) {
            throw new DomainArgumentException(ErrorCode.CENTER_CHAT_REPORT_ALREADY_PENDING);
        }
        CenterChatReportJpaEntity r = new CenterChatReportJpaEntity();
        r.setThreadId(t.getId());
        r.setReporterMemberId(viewer.memberId());
        r.setReporterRole(viewer.role().name());
        r.setReason(reason.name());
        r.setDetail(detail);
        r.setReportedUpToMessageId(t.getLastMessageId());
        r.setStatus(ReportStatus.PENDING.name());
        r.setActiveKey(key);
        r.setCreatedAt(clock.instant());
        try {
            r = reports.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw new DomainArgumentException(ErrorCode.CENTER_CHAT_REPORT_ALREADY_PENDING);
        }
        notifier.reported(r.getId(), ChatPlaceLookup.text(places.find(t.getPlaceId()).orElse(null), "name"), reason);
        return mine(r);
    }

    @Transactional(readOnly = true)
    public ChatViews.Page<ChatViews.AdminReportSummary> list(String statusRaw, String cursorRaw, Integer sizeRaw) {
        String status;
        if (statusRaw == null || statusRaw.isBlank()) {
            status = ReportStatus.PENDING.name();
        } else if ("ALL".equalsIgnoreCase(statusRaw.trim())) {
            status = null;
        } else {
            ReportStatus s = ChatEnums.parse(ReportStatus.class, statusRaw);
            if (s == null) {
                throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
            }
            status = s.name();
        }
        int size = ChatInboxService.size(sizeRaw);
        List<CenterChatReportJpaEntity> rows = reports.page(status, ChatInboxService.cursor(cursorRaw), PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        List<CenterChatReportJpaEntity> page = rows.stream().limit(size).toList();
        Map<Long, CenterChatThreadJpaEntity> threadMap = new java.util.HashMap<>();
        threads.findAllById(page.stream().map(CenterChatReportJpaEntity::getThreadId).toList()).forEach(t -> threadMap.put(t.getId(), t));
        Map<String, PlaceJpaEntity> placeMap = places.findAll(threadMap.values().stream().map(CenterChatThreadJpaEntity::getPlaceId).toList());
        Map<String, MemberDirectory.MemberBrief> briefs = members.findBriefs(page.stream().map(CenterChatReportJpaEntity::getReporterMemberId).toList());
        List<ChatViews.AdminReportSummary> items = new ArrayList<>();
        for (CenterChatReportJpaEntity r : page) {
            CenterChatThreadJpaEntity t = threadMap.get(r.getThreadId());
            items.add(
                    new ChatViews.AdminReportSummary(
                            String.valueOf(r.getId()),
                            new ChatViews.AdminThreadRef(
                                    String.valueOf(r.getThreadId()),
                                    t == null ? null : t.getPlaceId(),
                                    t == null ? null : ChatPlaceLookup.text(placeMap.get(t.getPlaceId()), "name"),
                                    t == null ? null : t.getStatus()),
                            reporter(r, briefs),
                            r.getReason(),
                            r.getStatus(),
                            ApiTimestamps.format(r.getCreatedAt()),
                            ApiTimestamps.format(r.getResolvedAt())));
        }
        return new ChatViews.Page<>(items, more ? items.get(items.size() - 1).id() : null);
    }

    @Transactional(readOnly = true)
    public ChatViews.AdminReportDetail detail(String reportIdRaw) {
        return detailOf(reports.findById(ChatAccess.parseId(reportIdRaw)).orElseThrow(ChatAccess::notFound));
    }

    /**
     * 처리(C5.3). DISMISS = 이 신고만, BLOCK_THREAD = 대화 BLOCKED + 그 대화의 PENDING 신고 전부 RESOLVED. 이미 처리됨(동시 처리
     * 포함) 409. 처리된 신고마다 신고자 알림, 차단이면 양쪽 SSE chat-thread.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ChatViews.AdminReportDetail resolve(String adminMemberId, String reportIdRaw, String actionRaw, String noteRaw) {
        ReportAction action = ChatEnums.parse(ReportAction.class, actionRaw);
        String note = noteRaw == null || noteRaw.isBlank() ? null : noteRaw.trim();
        if (action == null || (note != null && note.length() > 500)) {
            throw DetailedDomainException.fields(
                    ErrorCode.VALIDATION_FAILED,
                    action == null ? Map.of("action", "DISMISS 또는 BLOCK_THREAD 입니다.") : Map.of("note", "500자 이하여야 합니다."));
        }
        Long reportId = ChatAccess.parseId(reportIdRaw);
        // 잠금 순서 고정: 대화 행 → 신고 행. 같은 대화의 다른 신고를 동시에 처리해도 교착하지 않는다.
        Long threadId = reports.findThreadIdById(reportId).orElseThrow(ChatAccess::notFound);
        CenterChatThreadJpaEntity t = threads.findByIdForUpdate(threadId).orElseThrow(ChatAccess::notFound);
        CenterChatReportJpaEntity r = reports.findByIdForUpdate(reportId).orElseThrow(ChatAccess::notFound);
        if (!ReportStatus.PENDING.name().equals(r.getStatus())) {
            throw new DomainArgumentException(ErrorCode.CENTER_CHAT_REPORT_ALREADY_RESOLVED);
        }
        Instant now = clock.instant();
        List<CenterChatReportJpaEntity> done = new ArrayList<>();
        if (action == ReportAction.DISMISS) {
            close(r, ReportStatus.DISMISSED, action, note, adminMemberId, now);
            done.add(r);
        } else {
            t.setStatus(ThreadStatus.BLOCKED.name());
            t.setBlockedAt(now);
            threads.save(t);
            for (CenterChatReportJpaEntity p : reports.pendingForThreadForUpdate(t.getId())) {
                close(p, ReportStatus.RESOLVED, action, note, adminMemberId, now);
                done.add(p);
            }
            events.publishEvent(new ChatEvents.ThreadStatusChanged(t.getId(), ThreadStatus.BLOCKED.name()));
        }
        reports.saveAll(done);
        reports.flush();
        String placeName = ChatPlaceLookup.text(places.find(t.getPlaceId()).orElse(null), "name");
        for (CenterChatReportJpaEntity d : done) {
            notifier.resolved(d.getId(), t.getId(), d.getReporterMemberId(), SenderRole.valueOf(d.getReporterRole()), placeName);
        }
        return detailOf(reports.findById(reportId).orElseThrow());
    }

    private static void close(
            CenterChatReportJpaEntity r, ReportStatus status, ReportAction action, String note, String admin, Instant now) {
        r.setStatus(status.name());
        r.setActiveKey(null);
        r.setResolutionAction(action.name());
        r.setResolutionNote(note);
        r.setResolvedByMemberId(admin);
        r.setResolvedAt(now);
    }

    /** 관리자 상세의 대화 id(열람 로그용). */
    @Transactional(readOnly = true)
    public Long threadIdOf(String reportIdRaw) {
        return reports.findById(ChatAccess.parseId(reportIdRaw)).orElseThrow(ChatAccess::notFound).getThreadId();
    }

    private ChatViews.AdminReportDetail detailOf(CenterChatReportJpaEntity r) {
        CenterChatThreadJpaEntity t = threads.findById(r.getThreadId()).orElseThrow(ChatAccess::notFound);
        PlaceJpaEntity place = places.find(t.getPlaceId()).orElse(null);
        String owner = ownership.ownerOf(t.getPlaceId()).orElse(null);
        Map<String, MemberDirectory.MemberBrief> briefs =
                members.findBriefs(Arrays.asList(r.getReporterMemberId(), t.getCustomerMemberId(), owner, r.getResolvedByMemberId()));
        Map<String, String> names = members.displayNames(List.of(t.getCustomerMemberId()));
        MemberDirectory.MemberBrief customer = briefs.get(t.getCustomerMemberId());
        ChatViews.Resolution resolution = null;
        if (r.getResolvedAt() != null) {
            MemberDirectory.MemberBrief by = briefs.get(r.getResolvedByMemberId());
            resolution =
                    new ChatViews.Resolution(
                            r.getResolutionAction(),
                            r.getResolutionNote(),
                            new ChatViews.StaffRef(r.getResolvedByMemberId(), by == null ? null : by.loginId()),
                            ApiTimestamps.format(r.getResolvedAt()));
        }
        return new ChatViews.AdminReportDetail(
                String.valueOf(r.getId()),
                new ChatViews.AdminThreadDetailRef(
                        String.valueOf(t.getId()),
                        t.getPlaceId(),
                        ChatPlaceLookup.text(place, "name"),
                        t.getStatus(),
                        t.getProgramId() == null ? null : new ChatViews.ProgramRef(t.getProgramId(), t.getProgramTitle()),
                        ApiTimestamps.format(t.getCreatedAt())),
                reporter(r, briefs),
                new ChatViews.AdminCustomer(
                        t.getCustomerMemberId(), customer == null ? null : customer.email(), names.get(t.getCustomerMemberId())),
                owner == null ? null : new ChatViews.MemberEmail(owner, briefs.get(owner) == null ? null : briefs.get(owner).email()),
                r.getReason(),
                r.getDetail(),
                String.valueOf(r.getReportedUpToMessageId()),
                r.getStatus(),
                resolution,
                reports.countByThreadIdAndIdNot(t.getId(), r.getId()),
                ApiTimestamps.format(r.getCreatedAt()));
    }

    private static ChatViews.Reporter reporter(CenterChatReportJpaEntity r, Map<String, MemberDirectory.MemberBrief> briefs) {
        MemberDirectory.MemberBrief b = briefs.get(r.getReporterMemberId());
        return new ChatViews.Reporter(r.getReporterMemberId(), b == null ? null : b.email(), r.getReporterRole());
    }

    private static ChatViews.Report mine(CenterChatReportJpaEntity r) {
        return new ChatViews.Report(
                String.valueOf(r.getId()), String.valueOf(r.getThreadId()), r.getReason(), r.getDetail(), r.getStatus(),
                ApiTimestamps.format(r.getCreatedAt()));
    }
}
