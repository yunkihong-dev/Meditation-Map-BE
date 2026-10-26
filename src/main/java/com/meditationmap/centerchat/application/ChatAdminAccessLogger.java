package com.meditationmap.centerchat.application;

import com.meditationmap.centerchat.domain.ChatEnums.AccessKind;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatAdminAccessLogJpaEntity;
import com.meditationmap.centerchat.infrastructure.jpa.CenterChatAdminAccessLogSpringDataRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 열람 로그(C5.5, DC3). 별도 트랜잭션 — 실패해도 열람은 막지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAdminAccessLogger {

    private final CenterChatAdminAccessLogSpringDataRepository logs;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long threadId, String adminMemberId, AccessKind kind) {
        try {
            CenterChatAdminAccessLogJpaEntity e = new CenterChatAdminAccessLogJpaEntity();
            e.setThreadId(threadId);
            e.setAdminMemberId(adminMemberId);
            e.setAccessKind(kind.name());
            e.setAccessedAt(clock.instant());
            logs.save(e);
        } catch (RuntimeException ex) {
            log.warn("[CHAT] 관리자 열람 로그 저장 실패 thread={} admin={}", threadId, adminMemberId, ex);
        }
    }
}
