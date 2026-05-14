package com.meditationmap.notice.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.notice.application.NoticeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notices")
@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeQueryService noticeQueryService;

    @Operation(summary = "공지 목록 (카드용 요약 필드만)")
    @GetMapping("/notices")
    public List<JsonNode> list() {
        return noticeQueryService.listSummaries();
    }

    @Operation(summary = "공지 단건")
    @GetMapping("/notices/{id}")
    public ResponseEntity<JsonNode> byId(@PathVariable String id) {
        JsonNode payload = noticeQueryService.findPayloadByIdOrNull(id);
        if (payload == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(payload);
    }
}
