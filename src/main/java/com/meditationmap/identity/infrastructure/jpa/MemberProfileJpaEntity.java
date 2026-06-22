package com.meditationmap.identity.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "member_profiles")
@Getter
@Setter
@NoArgsConstructor
public class MemberProfileJpaEntity {

    @Id
    @Column(name = "member_id", length = 36, columnDefinition = "CHAR(36)")
    private String memberId;

    @Column(name = "display_name", length = 80)
    private String displayName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "region_ids", nullable = false, columnDefinition = "json")
    private JsonNode regionIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interests", nullable = false, columnDefinition = "json")
    private JsonNode interests;
}
