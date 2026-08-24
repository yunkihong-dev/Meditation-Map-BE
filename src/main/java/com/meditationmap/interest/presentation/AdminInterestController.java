package com.meditationmap.interest.presentation;

import com.meditationmap.interest.application.InterestService;
import com.meditationmap.interest.presentation.dto.InterestResponse;
import com.meditationmap.interest.presentation.dto.InterestUpsertRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin")
@RestController
@RequestMapping("/admin/interests")
@RequiredArgsConstructor
public class AdminInterestController {

    private final InterestService interests;

    @Operation(summary = "관심사 전체 목록 (내려둔 항목 포함)")
    @GetMapping
    public List<InterestResponse> list() {
        return interests.listAll();
    }

    @Operation(summary = "관심사 추가")
    @PostMapping
    public InterestResponse create(@Valid @RequestBody InterestUpsertRequest body) {
        return interests.create(body);
    }

    @Operation(summary = "관심사 수정")
    @PutMapping("/{id}")
    public InterestResponse update(
            @PathVariable("id") String id, @Valid @RequestBody InterestUpsertRequest body) {
        return interests.update(id, body);
    }

    @Operation(summary = "관심사 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        interests.delete(id);
        return ResponseEntity.noContent().build();
    }
}
