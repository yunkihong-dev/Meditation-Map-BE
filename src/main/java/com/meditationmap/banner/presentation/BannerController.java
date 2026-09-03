package com.meditationmap.banner.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.banner.application.BannerQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Banners")
@RestController
@RequiredArgsConstructor
public class BannerController {

    private final BannerQueryService bannerQueryService;

    @Operation(summary = "홈 배너 목록 (게시 기간 안이고 켜져 있는 것만, 정렬 순서대로)")
    @GetMapping("/banners")
    public List<JsonNode> list() {
        return bannerQueryService.listVisible();
    }
}
