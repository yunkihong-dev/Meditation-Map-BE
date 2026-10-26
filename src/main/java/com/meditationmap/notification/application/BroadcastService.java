package com.meditationmap.notification.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.notification.domain.LinkUrlPolicy;
import com.meditationmap.notification.domain.NotificationRefType;
import com.meditationmap.notification.domain.NotificationType;
import com.meditationmap.notification.infrastructure.jdbc.NotificationFanoutJdbcRepository;
import com.meditationmap.notification.infrastructure.jdbc.NotificationFanoutJdbcRepository.FanoutRow;
import com.meditationmap.notification.infrastructure.jpa.NotificationBroadcastJpaEntity;
import com.meditationmap.notification.infrastructure.jpa.NotificationBroadcastSpringDataRepository;
import com.meditationmap.shared.domain.ApiTimestamps;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 광고 알림 발송 (계약 3.1, D3·D6·D7).
 *
 * <ol>
 *   <li>KST 21:00~08:00 은 거부(정보통신망법 야간 광고 별도 동의를 받지 않으므로)
 *   <li>requestId 중복이면 409 — 더블클릭·재시도 방지. 사전 조회 + 유니크 키 이중 방어
 *   <li>이력 1행 → INSERT…SELECT 팬아웃(동의자만). 수신자가 0명이면 400 이고 이력도 롤백된다
 *   <li>제목에 {@code (광고) } 를 붙여 저장한다(이미 붙어 있으면 그대로)
 * </ol>
 *
 * 202 응답 시점에 알림 행은 커밋돼 있고, 전달은 커밋 후 비동기다.
 */
@Service
@RequiredArgsConstructor
public class BroadcastService {

    static final String AD_PREFIX = "(광고) ";
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final LocalTime QUIET_START = LocalTime.of(21, 0);
    static final LocalTime QUIET_END = LocalTime.of(8, 0);

    private final NotificationBroadcastSpringDataRepository broadcastRepository;
    private final NotificationFanoutJdbcRepository fanoutRepository;
    private final MemberDirectory memberDirectory;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public BroadcastResult send(String senderMemberId, BroadcastCommand cmd) {
        if (!LinkUrlPolicy.isAllowed(cmd.linkUrl())) {
            throw new DomainArgumentException(ErrorCode.INVALID_LINK_URL);
        }
        Instant now = clock.instant();
        if (isQuietHours(now)) {
            throw new DomainArgumentException(ErrorCode.BROADCAST_QUIET_HOURS);
        }
        if (broadcastRepository.existsByRequestId(cmd.requestId())) {
            throw new DomainArgumentException(ErrorCode.BROADCAST_DUPLICATE);
        }

        List<String> targetIds = List.of();
        List<String> unknownEmails = List.of();
        if (cmd.target() == Target.EMAILS) {
            MemberDirectory.EmailResolution r = memberDirectory.resolveNonStaffByEmails(cmd.emails());
            targetIds = r.memberIds();
            unknownEmails = r.unknownEmails();
        }

        String title = withAdPrefix(cmd.title().trim());
        NotificationBroadcastJpaEntity history = new NotificationBroadcastJpaEntity();
        history.setRequestId(cmd.requestId());
        history.setTitle(title);
        history.setBody(cmd.body());
        history.setLinkUrl(cmd.linkUrl());
        history.setTargetType(cmd.target().name());
        history.setRecipientCount(0);
        history.setSentByMemberId(senderMemberId);
        history.setCreatedAt(now);
        try {
            history = broadcastRepository.saveAndFlush(history);
        } catch (DataIntegrityViolationException e) {
            throw new DomainArgumentException(ErrorCode.BROADCAST_DUPLICATE);
        }

        FanoutRow row =
                new FanoutRow(
                        NotificationType.AD.name(),
                        title,
                        cmd.body(),
                        cmd.linkUrl(),
                        NotificationRefType.BROADCAST.name(),
                        String.valueOf(history.getId()),
                        history.getId(),
                        now);
        int count =
                cmd.target() == Target.ALL
                        ? fanoutRepository.insertForAllConsented(row)
                        : fanoutRepository.insertForConsentedMembers(row, targetIds);
        if (count == 0) {
            // 롤백되므로 이력 행도 남지 않는다(계약 3.1).
            throw new DomainArgumentException(ErrorCode.BROADCAST_NO_RECIPIENTS);
        }
        history.setRecipientCount(count);
        broadcastRepository.save(history);
        events.publishEvent(new NotificationEvents.BroadcastCreated(history.getId()));
        return new BroadcastResult(String.valueOf(history.getId()), count, unknownEmails);
    }

    @Transactional(readOnly = true)
    public CursorPage<BroadcastView> history(CursorRequest request) {
        List<NotificationBroadcastJpaEntity> rows =
                request.cursor() == null
                        ? broadcastRepository.findAllByOrderByIdDesc(request.probe())
                        : broadcastRepository.findByIdLessThanOrderByIdDesc(request.cursor(), request.probe());
        boolean hasMore = rows.size() > request.size();
        List<NotificationBroadcastJpaEntity> page = rows.stream().limit(request.size()).toList();
        Map<String, MemberDirectory.MemberBrief> senders =
                memberDirectory.findBriefs(page.stream().map(NotificationBroadcastJpaEntity::getSentByMemberId).toList());
        List<BroadcastView> items =
                page.stream()
                        .map(b -> {
                            MemberDirectory.MemberBrief s = senders.get(b.getSentByMemberId());
                            return new BroadcastView(
                                    String.valueOf(b.getId()),
                                    b.getTitle(),
                                    b.getBody(),
                                    b.getLinkUrl(),
                                    b.getTargetType(),
                                    b.getRecipientCount(),
                                    new SentBy(b.getSentByMemberId(), s == null ? null : s.loginId()),
                                    ApiTimestamps.format(b.getCreatedAt()));
                        })
                        .toList();
        String next = hasMore && !items.isEmpty() ? items.get(items.size() - 1).id() : null;
        return new CursorPage<>(items, next);
    }

    static boolean isQuietHours(Instant now) {
        LocalTime t = now.atZone(KST).toLocalTime();
        return !t.isBefore(QUIET_START) || t.isBefore(QUIET_END);
    }

    static String withAdPrefix(String title) {
        if (title.startsWith("(광고)")) {
            return title;
        }
        return AD_PREFIX + title;
    }

    public enum Target {
        ALL,
        EMAILS
    }

    public record BroadcastCommand(
            String requestId, String title, String body, String linkUrl, Target target, List<String> emails) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BroadcastResult(String broadcastId, int recipientCount, List<String> unknownEmails) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SentBy(String memberId, String loginId) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BroadcastView(
            String id,
            String title,
            String body,
            String linkUrl,
            String target,
            int recipientCount,
            SentBy sentBy,
            String createdAt) {}
}
