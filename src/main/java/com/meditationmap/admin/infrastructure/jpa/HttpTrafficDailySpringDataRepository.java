package com.meditationmap.admin.infrastructure.jpa;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HttpTrafficDailySpringDataRepository extends JpaRepository<HttpTrafficDailyJpaEntity, LocalDate> {

    List<HttpTrafficDailyJpaEntity> findByTrafficDateBetweenOrderByTrafficDateAsc(
            LocalDate startInclusive, LocalDate endInclusive);

    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO admin_http_traffic_daily (traffic_date, request_count)
                    VALUES (:date, 1)
                    ON DUPLICATE KEY UPDATE request_count = request_count + 1
                    """,
            nativeQuery = true)
    void incrementForDate(@Param("date") LocalDate date);
}
