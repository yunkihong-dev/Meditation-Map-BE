package com.meditationmap.interest.presentation;

import com.meditationmap.interest.application.InterestService;
import com.meditationmap.interest.presentation.dto.InterestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Interests")
@RestController
@RequiredArgsConstructor
public class InterestController {

    private final InterestService interests;

    @Operation(summary = "관심사 목록 (노출 중인 것만)")
    @GetMapping("/interests")
    public List<InterestResponse> list() {
        return interests.listActive();
    }
}
