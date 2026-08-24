package com.meditationmap.classtype.presentation;

import com.meditationmap.classtype.application.ClassTypeService;
import com.meditationmap.classtype.presentation.dto.ClassTypeResponse;
import com.meditationmap.classtype.presentation.dto.ClassTypeUpsertRequest;
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
@RequestMapping("/admin/class-types")
@RequiredArgsConstructor
public class AdminClassTypeController {

    private final ClassTypeService classTypes;

    @Operation(summary = "클래스 종류 전체 목록 (내려둔 항목 포함)")
    @GetMapping
    public List<ClassTypeResponse> list() {
        return classTypes.listAll();
    }

    @Operation(summary = "클래스 종류 추가")
    @PostMapping
    public ClassTypeResponse create(@Valid @RequestBody ClassTypeUpsertRequest body) {
        return classTypes.create(body);
    }

    @Operation(summary = "클래스 종류 수정")
    @PutMapping("/{id}")
    public ClassTypeResponse update(
            @PathVariable("id") String id, @Valid @RequestBody ClassTypeUpsertRequest body) {
        return classTypes.update(id, body);
    }

    @Operation(summary = "클래스 종류 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        classTypes.delete(id);
        return ResponseEntity.noContent().build();
    }
}
