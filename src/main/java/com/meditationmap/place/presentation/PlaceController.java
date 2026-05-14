package com.meditationmap.place.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.place.application.PlaceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Places")
@RestController
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceQueryService placeQueryService;

    @Operation(summary = "명상 장소 목록 (FE GET /places)")
    @GetMapping("/places")
    public List<JsonNode> list(@RequestParam(defaultValue = "all") String regionId) {
        return placeQueryService.listPlaces(regionId);
    }

    @Operation(summary = "명상 장소 단건 (FE GET /places/:id)")
    @GetMapping("/places/{id}")
    public ResponseEntity<JsonNode> byId(@PathVariable("id") String id) {
        JsonNode data = placeQueryService.findByIdOrNull(id);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }
}
