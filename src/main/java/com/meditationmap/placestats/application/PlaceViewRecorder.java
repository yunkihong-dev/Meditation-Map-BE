package com.meditationmap.placestats.application;

import com.meditationmap.place.infrastructure.jpa.PlaceSpringDataRepository;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.placestats.infrastructure.jdbc.PlaceStatsJdbcRepository;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 상세 조회 기록 (POST /places/{id}/views 비콘).
 *
 * <p>세지 않는 것: 봇·크롤러·빈 UA, 스태프 세션(관리자 쿠키), 그 장소의 오너 본인. 같은 조회자는 장소별 KST 하루 1회.
 * 조회자 키는 회원이면 회원 ID, 비로그인이면 IP+UA — 쿠키 키를 쓰지 않는 이유는 쿠키를 지우며 연타하면 무한히 부풀기 때문이다
 * (같은 공유기 뒤 여러 사람이 하루 1회로 묶이는 과소 집계를 대신 감수).
 */
@Slf4j
@Service
public class PlaceViewRecorder {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern BOT =
            Pattern.compile(
                    "bot|crawl|spider|slurp|preview|headless|facebookexternalhit|kakaotalk-scrap|curl|wget|python|httpclient|java/|okhttp|go-http",
                    Pattern.CASE_INSENSITIVE);

    private final PlaceStatsJdbcRepository stats;
    private final PlaceSpringDataRepository places;
    private final PlaceOwnership ownership;
    private final Clock clock;
    private final String salt;

    public PlaceViewRecorder(
            PlaceStatsJdbcRepository stats,
            PlaceSpringDataRepository places,
            PlaceOwnership ownership,
            Clock clock,
            @Value("${app.jwt.secret}") String salt) {
        this.stats = stats;
        this.places = places;
        this.ownership = ownership;
        this.clock = clock;
        this.salt = salt;
    }

    /** @return 이번 요청으로 조회수가 올랐으면 true (응답에는 드러내지 않는다) */
    @Transactional
    public boolean record(String placeId, ViewerContext viewer) {
        if (placeId == null || !places.existsById(placeId)) {
            throw new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!shouldCount(placeId, viewer)) {
            return false;
        }
        LocalDate today = LocalDate.now(clock.withZone(KST));
        String key = viewer.memberId() != null ? "m:" + viewer.memberId() : "a:" + viewer.clientIp() + "|" + viewer.userAgent();
        if (!stats.markViewed(placeId, today, hash(today, key))) {
            return false;
        }
        stats.incrementViews(placeId, today);
        return true;
    }

    boolean shouldCount(String placeId, ViewerContext viewer) {
        if (viewer.staffSession()) {
            return false;
        }
        if (isBot(viewer.userAgent())) {
            return false;
        }
        if (viewer.memberId() == null && (viewer.clientIp() == null || viewer.clientIp().isBlank())) {
            return false;
        }
        return viewer.memberId() == null || !ownership.isOwner(viewer.memberId(), placeId);
    }

    static boolean isBot(String userAgent) {
        return userAgent == null || userAgent.isBlank() || BOT.matcher(userAgent).find();
    }

    String hash(LocalDate day, String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((salt + "|" + day + "|" + key.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 중복 억제 행은 이틀만 둔다(어제·오늘). 매일 KST 04:10. */
    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    public void purgeOldDedup() {
        int n = stats.deleteDedupBefore(LocalDate.now(clock.withZone(KST)).minusDays(1));
        log.info("[STATS] place_view_dedup 정리 {}행", n);
    }
}
