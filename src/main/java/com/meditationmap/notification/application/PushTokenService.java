package com.meditationmap.notification.application;

import com.meditationmap.notification.infrastructure.jpa.PushTokenJpaEntity;
import com.meditationmap.notification.infrastructure.jpa.PushTokenSpringDataRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FCM 토큰 등록·해제 (계약 2.5).
 *
 * <ul>
 *   <li>등록은 upsert. 다른 회원에게 등록돼 있던 토큰이면 현재 회원으로 옮긴다(토큰 하나 = 회원 한 명)
 *   <li>회원당 최대 10개. 넘으면 last_seen_at 이 가장 오래된 것부터 지운다
 *   <li>해제는 멱등. 남의 토큰은 지우지 않는다
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PushTokenService {

    public static final int MAX_TOKENS_PER_MEMBER = 10;
    public static final String PLATFORM_WEB = "WEB";

    private final PushTokenSpringDataRepository tokenRepository;
    private final Clock clock;

    @Transactional
    public void register(String memberId, String token, String platform) {
        Instant now = clock.instant();
        tokenRepository.upsert(
                token,
                memberId,
                platform == null ? PLATFORM_WEB : platform,
                LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        List<PushTokenJpaEntity> mine = tokenRepository.findByMemberIdOrderByLastSeenAtDescIdDesc(memberId);
        if (mine.size() > MAX_TOKENS_PER_MEMBER) {
            tokenRepository.deleteAll(mine.subList(MAX_TOKENS_PER_MEMBER, mine.size()));
        }
    }

    @Transactional
    public void unregister(String memberId, String token) {
        tokenRepository.deleteByTokenAndMemberId(token, memberId);
    }
}
