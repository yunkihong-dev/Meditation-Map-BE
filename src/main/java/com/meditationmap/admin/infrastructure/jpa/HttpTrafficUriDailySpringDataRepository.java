package com.meditationmap.admin.infrastructure.jpa;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HttpTrafficUriDailySpringDataRepository
        extends JpaRepository<HttpTrafficUriDailyJpaEntity, HttpTrafficUriDailyJpaEntity.Key> {

    List<HttpTrafficUriDailyJpaEntity> findByTrafficDateBetweenOrderByTrafficDateAscUriAsc(
            LocalDate startInclusive, LocalDate endInclusive);

    @Query(
            """
            SELECT e.uri AS uri, SUM(e.requestCount) AS total
            FROM HttpTrafficUriDailyJpaEntity e
            WHERE e.trafficDate BETWEEN :start AND :end
            GROUP BY e.uri
            ORDER BY total DESC
            """)
    List<UriTotalProjection> sumByUriBetween(
            @Param("start") LocalDate startInclusive, @Param("end") LocalDate endInclusive);

    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO admin_http_traffic_uri_daily (traffic_date, uri, request_count)
                    VALUES (:date, :uri, 1)
                    ON DUPLICATE KEY UPDATE request_count = request_count + 1
                    """,
            nativeQuery = true)
    void incrementForDateAndUri(@Param("date") LocalDate date, @Param("uri") String uri);

    interface UriTotalProjection {
        String getUri();

        long getTotal();
    }
}
