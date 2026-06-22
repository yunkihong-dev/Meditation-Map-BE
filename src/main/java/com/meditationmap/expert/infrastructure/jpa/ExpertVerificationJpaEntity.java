package com.meditationmap.expert.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "expert_verifications")
@Getter
@Setter
@NoArgsConstructor
public class ExpertVerificationJpaEntity {

    @Id
    @Column(name = "expert_id")
    private String expertId;

    @Column(name = "business_registration_number", length = 20)
    private String businessRegistrationNumber;

    @Column(name = "business_opening_date")
    private LocalDate businessOpeningDate;

    @Column(name = "verification_status", nullable = false, length = 20)
    private String verificationStatus = "PENDING";
}
