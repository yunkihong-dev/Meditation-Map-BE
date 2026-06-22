package com.meditationmap.place.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.place.application.PlaceProgramNormalizer;
import com.meditationmap.place.domain.PlaceId;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import com.meditationmap.place.domain.PlaceRepository;
import com.meditationmap.place.infrastructure.jdbc.PlaceSummaryJdbcRepository;
import com.meditationmap.region.domain.RegionId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceQueryService {

    private final PlaceRepository placeRepository;
    private final PlaceSummaryJdbcRepository placeSummaryJdbcRepository;
    private final ObjectMapper objectMapper;

    /**
     * 목록: JSON 전체 컬럼을 읽지 않고 DB에서 필요한 경로만 투영합니다 (단일 쿼리).
     */
    @Cacheable(value = "places", key = "#regionIdRaw")
    public List<JsonNode> listPlaces(String regionIdRaw) {
        RegionId scope = RegionId.of(regionIdRaw);
        return placeSummaryJdbcRepository.listByRegionId(scope.value());
    }

    @Cacheable(value = "place", key = "#id", unless = "#result == null")
    public JsonNode findByIdOrNull(String id) {
        return placeRepository
                .findById(PlaceId.of(id))
                .map(this::toJson)
                .orElse(null);
    }

    private JsonNode toJson(com.meditationmap.place.domain.Place p) {
        try {
            JsonNode node = objectMapper.readTree(p.getJsonPayload());
            if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
                return PlaceProgramNormalizer.normalizePlaceData(objectNode);
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new InfrastructureException(ErrorCode.PLACE_PAYLOAD_INVALID, e);
        }
    }
}
