package com.meditationmap.identity.application;

import com.meditationmap.identity.domain.Email;
import com.meditationmap.identity.domain.MemberNotFoundException;
import com.meditationmap.identity.domain.MemberRepository;
import com.meditationmap.identity.infrastructure.jpa.MemberFavoritePlaceJpaEntity;
import com.meditationmap.identity.infrastructure.jpa.MemberFavoritePlaceSpringDataRepository;
import com.meditationmap.place.infrastructure.jpa.PlaceSpringDataRepository;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FavoritePlacesApplicationService {

    private final MemberRepository memberRepository;
    private final MemberFavoritePlaceSpringDataRepository favoritePlaceSpringDataRepository;
    private final PlaceSpringDataRepository placeSpringDataRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<String> listPlaceIdsForEmail(String email) {
        var member =
                memberRepository
                        .findByEmail(Email.of(email))
                        .orElseThrow(MemberNotFoundException::new);
        return favoritePlaceSpringDataRepository.findAllById_MemberIdOrderByCreatedAtAsc(member.getId().value()).stream()
                .map(e -> e.getId().getPlaceId())
                .toList();
    }

    public List<String> replaceFavoritesForEmail(String email, List<String> placeIds) {
        var member =
                memberRepository
                        .findByEmail(Email.of(email))
                        .orElseThrow(MemberNotFoundException::new);
        String memberId = member.getId().value();
        Set<String> before = new LinkedHashSet<>(favoritePlaceSpringDataRepository.findPlaceIdsByMemberId(memberId));
        favoritePlaceSpringDataRepository.deleteAllForMember(memberId);
        List<String> distinct =
                placeIds == null
                        ? List.of()
                        : placeIds.stream().distinct().filter(id -> placeSpringDataRepository.existsById(id)).toList();
        if (!distinct.isEmpty()) {
            favoritePlaceSpringDataRepository.saveAll(
                    distinct.stream().map(pid -> new MemberFavoritePlaceJpaEntity(memberId, pid)).toList());
        }
        // 전체 교체 API 라서 이전 목록과 비교해 늘고 준 것만 통계로 넘긴다.
        Set<String> after = new LinkedHashSet<>(distinct);
        Set<String> added = new LinkedHashSet<>(after);
        added.removeAll(before);
        Set<String> removed = new LinkedHashSet<>(before);
        removed.removeAll(after);
        if (!added.isEmpty() || !removed.isEmpty()) {
            events.publishEvent(new FavoritePlacesChangedEvent(memberId, added, removed));
        }
        return distinct;
    }
}
