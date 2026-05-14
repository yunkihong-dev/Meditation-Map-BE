package com.meditationmap.identity.presentation;

import com.meditationmap.identity.application.FavoritePlacesApplicationService;
import com.meditationmap.identity.presentation.dto.FavoritesResponse;
import com.meditationmap.identity.presentation.dto.UpdateFavoritesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me — favorites")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FavoritePlacesController {

    private final FavoritePlacesApplicationService favoritePlacesApplicationService;

    @Operation(summary = "내 즐겨찾기 장소 id 목록")
    @GetMapping("/me/favorites")
    public FavoritesResponse list(@AuthenticationPrincipal UserDetails user) {
        var ids = favoritePlacesApplicationService.listPlaceIdsForEmail(user.getUsername());
        return new FavoritesResponse(ids);
    }

    @Operation(summary = "내 즐겨찾기 전체 교체")
    @PutMapping("/me/favorites")
    public FavoritesResponse replace(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody UpdateFavoritesRequest body) {
        var ids = favoritePlacesApplicationService.replaceFavoritesForEmail(user.getUsername(), body.placeIds());
        return new FavoritesResponse(ids);
    }
}
