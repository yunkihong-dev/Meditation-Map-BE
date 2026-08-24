package com.meditationmap.classtype.presentation;

import com.meditationmap.classtype.application.ClassTypeService;
import com.meditationmap.classtype.presentation.dto.ClassTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ClassTypes")
@RestController
@RequiredArgsConstructor
public class ClassTypeController {

    private final ClassTypeService classTypes;

    @Operation(summary = "클래스 종류 목록 (노출 중인 것만)")
    @GetMapping("/class-types")
    public List<ClassTypeResponse> list() {
        return classTypes.listActive();
    }
}
