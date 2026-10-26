package com.meditationmap.notification.presentation.dto;

import jakarta.validation.constraints.NotNull;

/** PUT /me/marketing-consent 본문 (D3, 계약 변경 C1). */
public record MarketingConsentRequest(@NotNull Boolean pushAgreed) {}
