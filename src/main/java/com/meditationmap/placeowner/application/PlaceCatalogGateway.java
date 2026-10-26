package com.meditationmap.placeowner.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.place.infrastructure.jpa.PlaceSpringDataRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * placeowner 가 장소(places) 를 보는 유일한 창구. 관리자 컨텍스트(AdminCatalogService)와 같은 관례로
 * {@link PlaceSpringDataRepository} 를 직접 쓰되, 여기 한곳으로 모은다.
 */
@Component
@RequiredArgsConstructor
public class PlaceCatalogGateway {

    private final PlaceSpringDataRepository placeRepository;

    public Optional<PlaceJpaEntity> find(String placeId) {
        if (placeId == null || placeId.isBlank()) {
            return Optional.empty();
        }
        return placeRepository.findById(placeId);
    }

    /** 장소 행 잠금(FOR UPDATE). 호출자 트랜잭션 안에서만 의미가 있다. */
    public Optional<PlaceJpaEntity> lock(String placeId) {
        return placeRepository.findByIdForUpdate(placeId);
    }

    public PlaceJpaEntity save(PlaceJpaEntity place) {
        return placeRepository.saveAndFlush(place);
    }

    public Map<String, PlaceJpaEntity> findAll(Collection<String> placeIds) {
        Map<String, PlaceJpaEntity> out = new HashMap<>();
        if (placeIds == null || placeIds.isEmpty()) {
            return out;
        }
        for (PlaceJpaEntity p : placeRepository.findAllById(new LinkedHashSet<>(placeIds))) {
            out.put(p.getId(), p);
        }
        return out;
    }

    public static String nameOf(PlaceJpaEntity place) {
        return place == null ? null : textOrNull(place.getData(), "name");
    }

    static String textOrNull(JsonNode data, String field) {
        if (data == null) {
            return null;
        }
        JsonNode v = data.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isEmpty() ? null : s;
    }
}
