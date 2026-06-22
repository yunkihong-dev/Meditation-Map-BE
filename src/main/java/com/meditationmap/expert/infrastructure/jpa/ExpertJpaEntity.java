package com.meditationmap.expert.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "experts")
@Setter
@Getter
public class ExpertJpaEntity {

    @Id
    private String id;

    @Column(name = "owner_member_id", unique = true, length = 36, columnDefinition = "CHAR(36)")
    private String ownerMemberId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", nullable = false, columnDefinition = "json")
    private JsonNode data;
}
