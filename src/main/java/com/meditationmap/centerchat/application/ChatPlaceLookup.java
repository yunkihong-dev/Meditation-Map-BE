package com.meditationmap.centerchat.application;

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

/** 채팅이 장소 payload 에서 읽는 것(이름·썸네일·숨김·프로그램)만. 장소는 바꾸지 않는다. */
@Component
@RequiredArgsConstructor
public class ChatPlaceLookup {

    private final PlaceSpringDataRepository places;

    public Optional<PlaceJpaEntity> find(String placeId) {
        return placeId == null || placeId.isBlank() ? Optional.empty() : places.findById(placeId);
    }

    public Map<String, PlaceJpaEntity> findAll(Collection<String> ids) {
        Map<String, PlaceJpaEntity> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (PlaceJpaEntity p : places.findAllById(new LinkedHashSet<>(ids))) {
            out.put(p.getId(), p);
        }
        return out;
    }

    public static String text(PlaceJpaEntity p, String field) {
        if (p == null || p.getData() == null) {
            return null;
        }
        JsonNode v = p.getData().get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    public static boolean hidden(PlaceJpaEntity p) {
        return p != null && p.getData() != null && p.getData().path("hidden").isBoolean() && p.getData().path("hidden").booleanValue();
    }

    /** 숨김이 아닌 프로그램이면 그 항목. */
    public static Optional<JsonNode> visibleProgram(PlaceJpaEntity p, String programId) {
        if (p == null || p.getData() == null || !p.getData().path("programs").isArray()) {
            return Optional.empty();
        }
        for (JsonNode prog : p.getData().get("programs")) {
            if (programId.equals(prog.path("id").asText(null))) {
                boolean hidden = prog.path("hidden").isBoolean() && prog.path("hidden").booleanValue();
                return hidden ? Optional.empty() : Optional.of(prog);
            }
        }
        return Optional.empty();
    }
}
