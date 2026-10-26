package com.meditationmap.notification.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meditationmap.notification.infrastructure.jpa.MarketingConsentJpaEntity;
import com.meditationmap.notification.infrastructure.jpa.MarketingConsentSpringDataRepository;
import com.meditationmap.shared.domain.ApiTimestamps;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 광고성 알림 수신 동의 조회·변경 (D3). 행이 없으면 미동의. */
@Service
@RequiredArgsConstructor
public class MarketingConsentService {

    private final MarketingConsentSpringDataRepository consentRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ConsentView get(String memberId) {
        return consentRepository.findById(memberId).map(ConsentView::from).orElse(ConsentView.NONE);
    }

    @Transactional
    public ConsentView set(String memberId, boolean agreed) {
        Instant now = clock.instant();
        MarketingConsentJpaEntity row =
                consentRepository.findById(memberId).orElseGet(() -> {
                    MarketingConsentJpaEntity e = new MarketingConsentJpaEntity();
                    e.setMemberId(memberId);
                    return e;
                });
        if (agreed && !row.isPushAgreed()) {
            row.setAgreedAt(now);
            row.setRevokedAt(null);
        } else if (!agreed && row.isPushAgreed()) {
            row.setRevokedAt(now);
        }
        row.setPushAgreed(agreed);
        row.setUpdatedAt(now);
        return ConsentView.from(consentRepository.save(row));
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ConsentView(boolean pushAgreed, String agreedAt, String revokedAt) {
        static final ConsentView NONE = new ConsentView(false, null, null);

        static ConsentView from(MarketingConsentJpaEntity e) {
            return new ConsentView(
                    e.isPushAgreed(),
                    e.isPushAgreed() ? ApiTimestamps.format(e.getAgreedAt()) : null,
                    ApiTimestamps.format(e.getRevokedAt()));
        }
    }
}
