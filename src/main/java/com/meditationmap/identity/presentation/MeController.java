package com.meditationmap.identity.presentation;

import com.meditationmap.identity.presentation.dto.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    @Operation(summary = "내 정보 (JWT 필요)")
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal UserDetails user) {
        return new MeResponse(user.getUsername());
    }
}
