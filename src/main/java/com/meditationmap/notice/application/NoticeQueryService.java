package com.meditationmap.notice.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.notice.infrastructure.jdbc.NoticeSummaryJdbcRepository;
import com.meditationmap.notice.infrastructure.jpa.NoticeJpaEntity;
import com.meditationmap.notice.infrastructure.jpa.NoticeSpringDataRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeQueryService {

    private final NoticeSpringDataRepository noticeSpringDataRepository;
    private final NoticeSummaryJdbcRepository noticeSummaryJdbcRepository;

    /** 목록: id·제목·요약 등만 (payload 전문 X, 단일 쿼리). */
    public List<JsonNode> listSummaries() {
        return noticeSummaryJdbcRepository.listSummaries();
    }

    public JsonNode findPayloadByIdOrNull(String id) {
        return noticeSpringDataRepository.findById(id).map(NoticeJpaEntity::getPayload).orElse(null);
    }
}
