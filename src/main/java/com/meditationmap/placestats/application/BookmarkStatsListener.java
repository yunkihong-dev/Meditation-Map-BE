package com.meditationmap.placestats.application;

import com.meditationmap.identity.application.FavoritePlacesChangedEvent;
import com.meditationmap.placestats.infrastructure.jdbc.PlaceStatsJdbcRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 북마크 변경을 같은 트랜잭션에서 일별 증감으로 기록한다(롤백되면 같이 롤백). */
@Component
@RequiredArgsConstructor
public class BookmarkStatsListener {

    private final PlaceStatsJdbcRepository stats;
    private final Clock clock;

    @EventListener
    public void on(FavoritePlacesChangedEvent e) {
        LocalDate today = LocalDate.now(clock.withZone(PlaceViewRecorder.KST));
        for (String placeId : e.added()) {
            stats.addBookmarkDelta(placeId, today, 1, 0);
        }
        for (String placeId : e.removed()) {
            stats.addBookmarkDelta(placeId, today, 0, 1);
        }
    }
}
