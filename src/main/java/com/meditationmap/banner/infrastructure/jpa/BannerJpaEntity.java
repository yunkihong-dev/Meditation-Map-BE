package com.meditationmap.banner.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 홈 중간 이미지 배너. 공지·장소와 같이 payload(JSON) 한 덩어리로 들고 있습니다.
 * 배너는 필드가 자주 늘어나는 편이라(문구, 링크, 노출 조건…) 컬럼을 쪼개지 않았습니다.
 */
@Entity
@Table(name = "banners")
@Setter
@Getter
public class BannerJpaEntity {

    @Id
    private String id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private JsonNode payload;
}
