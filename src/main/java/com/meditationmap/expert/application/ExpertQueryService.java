package com.meditationmap.expert.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.expert.domain.ExpertId;
import com.meditationmap.expert.domain.ExpertRepository;
import com.meditationmap.expert.infrastructure.jdbc.ExpertSummaryJdbcRepository;
import com.meditationmap.region.domain.RegionId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpertQueryService {

    private final ExpertRepository expertRepository;
    private final ExpertSummaryJdbcRepository expertSummaryJdbcRepository;
    private final ObjectMapper objectMapper;

    /** 목록: programs·reviews 등은 제외하고 단일 쿼리로 조회합니다. */
    @Cacheable(value = "experts", key = "#regionIdRaw")
    public List<JsonNode> listExperts(String regionIdRaw) {
        RegionId scope = RegionId.of(regionIdRaw);
        return expertSummaryJdbcRepository.listByRegionId(scope.value());
    }

    @Cacheable(value = "expert", key = "#id", unless = "#result == null")
    public JsonNode findByIdOrNull(String id) {
        return expertRepository.findById(ExpertId.of(id)).map(this::toJson).orElse(null);
    }

    private JsonNode toJson(com.meditationmap.expert.domain.Expert e) {
        try {
            return objectMapper.readTree(e.getJsonPayload());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("expert json corrupt id=" + e.getId(), ex);
        }
    }
}
