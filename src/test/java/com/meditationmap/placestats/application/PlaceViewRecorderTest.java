package com.meditationmap.placestats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meditationmap.place.infrastructure.jpa.PlaceSpringDataRepository;
import com.meditationmap.placeowner.application.PlaceOwnership;
import com.meditationmap.placestats.infrastructure.jdbc.PlaceStatsJdbcRepository;
import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 조회 기록 규칙 (라운드 3 계약 C6).
 *
 * <p>보장: 같은 조회자는 하루 1회(중복 행이면 증가 안 함) · 봇·스태프·오너 본인은 세지 않음 · KST 자정 경계로 날짜가 갈린다 ·
 * 조회자 해시는 날짜마다 다르고 원문 IP 가 들어가지 않는다.
 * <p>보장하지 않음: INSERT IGNORE / ON DUPLICATE KEY 의 MySQL 동작(런타임 실측).
 */
@ExtendWith(MockitoExtension.class)
class PlaceViewRecorderTest {

    private static final String UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) AppleWebKit/605.1.15 Safari/604.1";
    private static final String MEMBER = "11111111-1111-1111-1111-111111111111";

    @Mock private PlaceStatsJdbcRepository stats;
    @Mock private PlaceSpringDataRepository places;
    @Mock private PlaceOwnership ownership;

    private PlaceViewRecorder at(String isoUtc) {
        return new PlaceViewRecorder(stats, places, ownership, Clock.fixed(Instant.parse(isoUtc), ZoneOffset.UTC), "salt");
    }

    @Test
    @DisplayName("처음 본 조회자면 오늘 조회수 +1, 같은 날 두 번째는 증가하지 않는다")
    void 하루_1회() {
        PlaceViewRecorder r = at("2026-10-07T03:00:00Z");
        given(places.existsById("12")).willReturn(true);
        given(stats.markViewed(eq("12"), eq(LocalDate.parse("2026-10-07")), anyString())).willReturn(true, false);

        assertThat(r.record("12", new ViewerContext(null, "1.2.3.4", UA, false))).isTrue();
        assertThat(r.record("12", new ViewerContext(null, "1.2.3.4", UA, false))).isFalse();
        verify(stats, org.mockito.Mockito.times(1)).incrementViews("12", LocalDate.parse("2026-10-07"));
    }

    @Test
    @DisplayName("KST 자정 경계: 14:59:59Z 는 전날, 15:00:00Z 는 다음 날로 센다")
    void KST_자정() {
        given(places.existsById("12")).willReturn(true);
        given(stats.markViewed(eq("12"), any(), anyString())).willReturn(true);

        at("2026-10-06T14:59:59Z").record("12", new ViewerContext(MEMBER, "1.2.3.4", UA, false));
        at("2026-10-06T15:00:00Z").record("12", new ViewerContext(MEMBER, "1.2.3.4", UA, false));

        verify(stats).incrementViews("12", LocalDate.parse("2026-10-06"));
        verify(stats).incrementViews("12", LocalDate.parse("2026-10-07"));
    }

    @Test
    @DisplayName("봇·빈 UA·스태프 세션·오너 본인은 세지 않는다")
    void 제외_대상() {
        PlaceViewRecorder r = at("2026-10-07T03:00:00Z");
        given(places.existsById("12")).willReturn(true);
        given(ownership.isOwner(MEMBER, "12")).willReturn(true);

        assertThat(r.record("12", new ViewerContext(null, "1.2.3.4", "Googlebot/2.1", false))).isFalse();
        assertThat(r.record("12", new ViewerContext(null, "1.2.3.4", "", false))).isFalse();
        assertThat(r.record("12", new ViewerContext(null, "1.2.3.4", "curl/8.4.0", false))).isFalse();
        assertThat(r.record("12", new ViewerContext(null, "1.2.3.4", UA, true))).isFalse();
        assertThat(r.record("12", new ViewerContext(MEMBER, "1.2.3.4", UA, false))).isFalse();
        verify(stats, never()).markViewed(anyString(), any(), anyString());
        verify(stats, never()).incrementViews(anyString(), any());
    }

    @Test
    @DisplayName("없는 장소는 404")
    void 없는_장소() {
        given(places.existsById("x")).willReturn(false);
        assertThatThrownBy(() -> at("2026-10-07T03:00:00Z").record("x", new ViewerContext(null, "1.2.3.4", UA, false)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("조회자 해시: 64자 hex, 원문 IP 미포함, 날짜가 바뀌면 달라진다(하루 단위 솔트)")
    void 해시() {
        given(places.existsById("12")).willReturn(true);
        ArgumentCaptor<String> h = ArgumentCaptor.forClass(String.class);
        given(stats.markViewed(eq("12"), any(), h.capture())).willReturn(true);

        at("2026-10-07T03:00:00Z").record("12", new ViewerContext(null, "203.0.113.9", UA, false));
        at("2026-10-08T03:00:00Z").record("12", new ViewerContext(null, "203.0.113.9", UA, false));

        assertThat(h.getAllValues().get(0)).hasSize(64).doesNotContain("203.0.113.9");
        assertThat(h.getAllValues().get(0)).isNotEqualTo(h.getAllValues().get(1));
    }
}
