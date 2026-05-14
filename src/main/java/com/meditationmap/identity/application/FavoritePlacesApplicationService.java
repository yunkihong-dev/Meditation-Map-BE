package com.meditationmap.identity.application;

import com.meditationmap.identity.domain.Email;
import com.meditationmap.identity.domain.MemberRepository;
import com.meditationmap.identity.infrastructure.jpa.MemberFavoritePlaceJpaEntity;
import com.meditationmap.identity.infrastructure.jpa.MemberFavoritePlaceSpringDataRepository;
import com.meditationmap.place.infrastructure.jpa.PlaceSpringDataRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FavoritePlacesApplicationService {

    private final MemberRepository memberRepository;
    private final MemberFavoritePlaceSpringDataRepository favoritePlaceSpringDataRepository;
    private final PlaceSpringDataRepository placeSpringDataRepository;

    @Transactional(readOnly = true)
    public List<String> listPlaceIdsForEmail(String email) {
        var member =
                memberRepository
                        .findByEmail(Email.of(email))
                        .orElseThrow(() -> new IllegalStateException("member not found"));
        return favoritePlaceSpringDataRepository.findAllById_MemberIdOrderByCreatedAtAsc(member.getId().value()).stream()
                .map(e -> e.getId().getPlaceId())
                .toList();
    }

    public List<String> replaceFavoritesForEmail(String email, List<String> placeIds) {
        var member =
                memberRepository
                        .findByEmail(Email.of(email))
                        .orElseThrow(() -> new IllegalStateException("member not found"));
        String memberId = member.getId().value();
        favoritePlaceSpringDataRepository.deleteAllForMember(memberId);
        if (placeIds == null || placeIds.isEmpty()) {
            return List.of();
        }
        var distinct =
                placeIds.stream().distinct().filter(id -> placeSpringDataRepository.existsById(id)).toList();
        favoritePlaceSpringDataRepository.saveAll(
                distinct.stream().map(pid -> new MemberFavoritePlaceJpaEntity(memberId, pid)).toList());
        return distinct;
    }
}
