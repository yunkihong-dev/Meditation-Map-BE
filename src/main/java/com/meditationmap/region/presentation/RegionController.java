package com.meditationmap.region.presentation;

import com.meditationmap.region.application.RegionQueryService;
import com.meditationmap.region.presentation.dto.RegionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Regions")
@RestController
@RequiredArgsConstructor
public class RegionController {

    private final RegionQueryService regionQueryService;

    @Operation(summary = "지역 목록 (FE GET /regions)")
    @GetMapping("/regions")
    public List<RegionResponse> list() {
        return regionQueryService.listRegions().stream()
                .map(r -> new RegionResponse(r.getId().value(), r.getName(), r.getSlug()))
                .toList();
    }
}
