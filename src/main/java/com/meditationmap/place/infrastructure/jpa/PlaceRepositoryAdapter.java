package com.meditationmap.place.infrastructure.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditationmap.place.domain.Place;
import com.meditationmap.place.domain.PlaceId;
import com.meditationmap.place.domain.PlaceRepository;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import com.meditationmap.region.domain.RegionId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaceRepositoryAdapter implements PlaceRepository {

    private final PlaceSpringDataRepository springData;
    private final ObjectMapper objectMapper;

    @Override
    public List<Place> findAll() {
        return springData.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Place> findByRegionId(RegionId regionId) {
        return springData.findByRegionId(regionId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Place> findById(PlaceId id) {
        return springData.findById(id.value()).map(this::toDomain);
    }

    private Place toDomain(PlaceJpaEntity e) {
        try {
            String json = objectMapper.writeValueAsString(e.getData());
            return new Place(PlaceId.of(e.getId()), RegionId.of(e.getRegionId()), json);
        } catch (JsonProcessingException ex) {
            throw new InfrastructureException(ErrorCode.PLACE_SERIALIZATION_FAILED, ex);
        }
    }
}
