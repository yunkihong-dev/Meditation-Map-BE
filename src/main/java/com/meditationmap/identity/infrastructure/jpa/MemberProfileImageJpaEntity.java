package com.meditationmap.identity.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "member_profile_images",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_member_profile_images_member_file",
                        columnNames = {"member_id", "file_id"}))
@Getter
@Setter
@NoArgsConstructor
public class MemberProfileImageJpaEntity {

    @Id
    @Column(name = "id", length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "member_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String memberId;

    @Column(name = "file_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String fileId;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryImage;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
