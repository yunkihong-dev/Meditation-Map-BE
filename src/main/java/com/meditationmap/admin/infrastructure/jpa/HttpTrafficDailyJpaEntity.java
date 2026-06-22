package com.meditationmap.admin.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "admin_http_traffic_daily")
@Getter
@Setter
public class HttpTrafficDailyJpaEntity {

    @Id
    @Column(name = "traffic_date")
    private LocalDate trafficDate;

    @Column(name = "request_count", nullable = false)
    private long requestCount;
}
