package com.meditationmap.inquiry.application;

import com.meditationmap.identity.domain.Email;
import com.meditationmap.identity.domain.MemberRepository;
import com.meditationmap.inquiry.infrastructure.jpa.InquiryJpaEntity;
import com.meditationmap.inquiry.infrastructure.jpa.InquirySpringDataRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InquiryApplicationService {

    private final InquirySpringDataRepository inquirySpringDataRepository;
    private final MemberRepository memberRepository;

    public void submit(String email, String subject, String body, Authentication authentication) {
        InquiryJpaEntity row = new InquiryJpaEntity();
        row.setId(UUID.randomUUID().toString());
        row.setEmail(email.trim());
        row.setSubject(subject.trim());
        row.setBody(body.trim());
        row.setCreatedAt(Instant.now());
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetails userDetails) {
            memberRepository
                    .findByEmail(Email.of(userDetails.getUsername()))
                    .ifPresent(m -> row.setUserId(m.getId().value()));
        }
        inquirySpringDataRepository.save(row);
    }
}
