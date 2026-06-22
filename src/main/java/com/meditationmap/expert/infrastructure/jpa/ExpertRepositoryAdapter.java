package com.meditationmap.expert.infrastructure.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.expert.domain.Expert;
import com.meditationmap.expert.domain.ExpertId;
import com.meditationmap.expert.domain.ExpertRepository;
import com.meditationmap.region.domain.RegionId;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpertRepositoryAdapter implements ExpertRepository {

    private final ExpertSpringDataRepository springData;
    private final ObjectMapper objectMapper;

    @Override
    public List<Expert> findAll() {
        return springData.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Expert> findById(ExpertId id) {
        return springData.findById(id.value()).map(this::toDomain);
    }

    private Expert toDomain(ExpertJpaEntity e) {
        JsonNode root = e.getData();
        Set<RegionId> regionIds = extractRegionIds(root);
        try {
            String json = objectMapper.writeValueAsString(root);
            return new Expert(ExpertId.of(e.getId()), json, regionIds);
        } catch (JsonProcessingException ex) {
            throw new InfrastructureException(ErrorCode.EXPERT_SERIALIZATION_FAILED, ex);
        }
    }

    private static Set<RegionId> extractRegionIds(JsonNode root) {
        Set<RegionId> out = new HashSet<>();
        JsonNode arr = root.get("regionIds");
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                if (n != null && n.isTextual()) {
                    out.add(RegionId.of(n.asText()));
                }
            }
        }
        return out;
    }
}
