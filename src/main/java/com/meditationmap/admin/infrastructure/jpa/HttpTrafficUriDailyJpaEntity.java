package com.meditationmap.admin.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admin_http_traffic_uri_daily")
@IdClass(HttpTrafficUriDailyJpaEntity.Key.class)
@Getter
@Setter
public class HttpTrafficUriDailyJpaEntity {

    @Id
    @Column(name = "traffic_date")
    private LocalDate trafficDate;

    @Id
    @Column(name = "uri", length = 200)
    private String uri;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private LocalDate trafficDate;
        private String uri;
    }
}
