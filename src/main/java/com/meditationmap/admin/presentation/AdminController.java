package com.meditationmap.admin.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meditationmap.place.application.PlaceProgramNormalizer;
import com.meditationmap.admin.application.AdminCatalogService;
import com.meditationmap.admin.application.AdminMetricsService;
import com.meditationmap.admin.application.AdminMetricsSeriesBuilder.AdminMetricsSeries;
import com.meditationmap.admin.application.ApiTrafficQueryService;
import com.meditationmap.admin.application.HttpTrafficQueryService;
import com.meditationmap.admin.application.MemberMetricsQueryService;
import com.meditationmap.admin.presentation.dto.AdminDto;
import com.meditationmap.expert.infrastructure.jpa.ExpertJpaEntity;
import com.meditationmap.notice.infrastructure.jpa.NoticeJpaEntity;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin", description = "관리자 콘텐츠·트래픽 (ADMIN/DEV/EMPLOYEE)")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'EMPLOYEE')")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminCatalogService catalogService;
    private final AdminMetricsService metricsService;
    private final HttpTrafficQueryService httpTrafficQueryService;
    private final MemberMetricsQueryService memberMetricsQueryService;
    private final ApiTrafficQueryService apiTrafficQueryService;

    @Operation(summary = "트래픽·카탈로그 집계")
    @GetMapping("/metrics/traffic")
    public AdminMetricsService.AdminTrafficSnapshot traffic() {
        return metricsService.trafficSnapshot();
    }

    @Operation(summary = "HTTP 요청 추이 (일별·월별)")
    @GetMapping("/metrics/http-traffic")
    public AdminMetricsSeries httpTraffic(
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(defaultValue = "30") int limit) {
        return httpTrafficQueryService.series(granularity, limit);
    }

    @Operation(summary = "회원 추이 (신규 가입·누적 회원)")
    @GetMapping("/metrics/member-traffic")
    public MemberMetricsQueryService.MemberMetricsBundle memberTraffic(
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(defaultValue = "30") int limit) {
        return memberMetricsQueryService.memberSeries(granularity, limit);
    }

    @Operation(summary = "API별 호출 추이")
    @GetMapping("/metrics/api-traffic")
    public ApiTrafficQueryService.ApiTrafficSeriesBundle apiTraffic(
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "12") int top) {
        return apiTrafficQueryService.endpointSeries(granularity, limit, top);
    }

    @GetMapping("/places")
    public List<Map<String, Object>> listPlaces() {
        return catalogService.listPlaces().stream().map(this::placeRow).toList();
    }

    @GetMapping("/places/{id}")
    public Map<String, Object> getPlace(@PathVariable String id) {
        return placeRow(catalogService.getPlace(id));
    }

    @PostMapping("/places")
    public Map<String, Object> createPlace(@Valid @RequestBody AdminDto.AdminPlaceCreateRequest body) {
        return placeRow(catalogService.createPlace(body.regionId(), body.data()));
    }

    @PutMapping("/places/{id}")
    public Map<String, Object> updatePlace(
            @PathVariable String id, @Valid @RequestBody AdminDto.AdminPlaceUpdateRequest body) {
        return placeRow(catalogService.updatePlace(id, body.regionId(), body.data()));
    }

    @DeleteMapping("/places/{id}")
    public ResponseEntity<Void> deletePlace(@PathVariable String id) {
        catalogService.deletePlace(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/experts")
    public List<Map<String, Object>> listExperts() {
        return catalogService.listExperts().stream().map(this::expertRow).toList();
    }

    @GetMapping("/experts/{id}")
    public Map<String, Object> getExpert(@PathVariable String id) {
        return expertRow(catalogService.getExpert(id));
    }

    @PostMapping("/experts")
    public Map<String, Object> createExpert(@Valid @RequestBody AdminDto.AdminExpertCreateRequest body) {
        return expertRow(catalogService.createExpert(body.data()));
    }

    @PutMapping("/experts/{id}")
    public Map<String, Object> updateExpert(
            @PathVariable String id, @Valid @RequestBody AdminDto.AdminExpertUpdateRequest body) {
        return expertRow(catalogService.updateExpert(id, body.data()));
    }

    @DeleteMapping("/experts/{id}")
    public ResponseEntity<Void> deleteExpert(@PathVariable String id) {
        catalogService.deleteExpert(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notices")
    public List<Map<String, Object>> listNotices() {
        return catalogService.listNotices().stream().map(this::noticeRow).toList();
    }

    @PostMapping("/notices")
    public Map<String, Object> createNotice(@Valid @RequestBody AdminDto.AdminNoticeCreateRequest body) {
        return noticeRow(catalogService.createNotice(body.payload()));
    }

    @PutMapping("/notices/{id}")
    public Map<String, Object> updateNotice(
            @PathVariable String id, @Valid @RequestBody AdminDto.AdminNoticeUpdateRequest body) {
        return noticeRow(catalogService.updateNotice(id, body.payload()));
    }

    @DeleteMapping("/notices/{id}")
    public ResponseEntity<Void> deleteNotice(@PathVariable String id) {
        catalogService.deleteNotice(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> placeRow(PlaceJpaEntity e) {
        JsonNode data = e.getData();
        if (data instanceof ObjectNode objectNode) {
            data = PlaceProgramNormalizer.normalizePlaceData(objectNode.deepCopy());
        }
        String name = data != null && data.has("name") ? data.get("name").asText("") : "";
        return Map.of("id", e.getId(), "regionId", e.getRegionId(), "name", name, "data", data);
    }

    private Map<String, Object> expertRow(ExpertJpaEntity e) {
        JsonNode data = e.getData();
        String name = data != null && data.has("name") ? data.get("name").asText("") : "";
        return Map.of("id", e.getId(), "name", name, "data", data);
    }

    private Map<String, Object> noticeRow(NoticeJpaEntity e) {
        JsonNode payload = e.getPayload();
        String title = payload != null && payload.has("title") ? payload.get("title").asText("") : "";
        return Map.of("id", e.getId(), "title", title, "payload", payload);
    }
}
