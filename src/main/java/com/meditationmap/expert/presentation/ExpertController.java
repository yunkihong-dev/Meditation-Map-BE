package com.meditationmap.expert.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.expert.application.ExpertQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Experts")
@RestController
@RequiredArgsConstructor
public class ExpertController {

    private final ExpertQueryService expertQueryService;

    @Operation(summary = "전문가 목록 (FE GET /experts)")
    @GetMapping("/experts")
    public List<JsonNode> list(@RequestParam(defaultValue = "all") String regionId) {
        return expertQueryService.listExperts(regionId);
    }

    @Operation(summary = "전문가 단건")
    @GetMapping("/experts/{id}")
    public ResponseEntity<JsonNode> byId(@PathVariable("id") String id) {
        JsonNode data = expertQueryService.findByIdOrNull(id);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }
}
