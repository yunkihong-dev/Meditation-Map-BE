package com.meditationmap.interest.application;

import com.meditationmap.interest.infrastructure.jpa.InterestJpaEntity;
import com.meditationmap.interest.infrastructure.jpa.InterestSpringDataRepository;
import com.meditationmap.interest.presentation.dto.InterestResponse;
import com.meditationmap.interest.presentation.dto.InterestUpsertRequest;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterestService {

    private final InterestSpringDataRepository repository;

    /** 사용자·전문가 화면용 — 내려둔 항목은 빼고 줍니다. */
    public List<InterestResponse> listActive() {
        return repository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(InterestService::toResponse)
                .toList();
    }

    /** 관리자 화면용 — 내려둔 항목까지 전부 줍니다. */
    public List<InterestResponse> listAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(InterestService::toResponse)
                .toList();
    }

    @Transactional
    public InterestResponse create(InterestUpsertRequest request) {
        String name = normalizeName(request.name());
        rejectDuplicate(name, null);

        InterestJpaEntity entity = new InterestJpaEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(name);
        entity.setDescription(trimToNull(request.description()));
        entity.setImageUrl(trimToNull(request.imageUrl()));
        entity.setActive(request.active() == null || request.active());
        entity.setSortOrder(request.sortOrder() != null ? request.sortOrder() : nextSortOrder());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public InterestResponse update(String id, InterestUpsertRequest request) {
        InterestJpaEntity entity =
                repository
                        .findById(id)
                        .orElseThrow(() -> new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND));
        String name = normalizeName(request.name());
        rejectDuplicate(name, id);

        entity.setName(name);
        entity.setDescription(trimToNull(request.description()));
        entity.setImageUrl(trimToNull(request.imageUrl()));
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        repository.deleteById(id);
    }

    private void rejectDuplicate(String name, String allowedId) {
        Optional<InterestJpaEntity> existing = repository.findByName(name);
        if (existing.isPresent() && !existing.get().getId().equals(allowedId)) {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
    }

    private int nextSortOrder() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream()
                        .mapToInt(InterestJpaEntity::getSortOrder)
                        .max()
                        .orElse(-1)
                + 1;
    }

    /** 이름이 곧 사용자·전문가 프로필에 저장되는 값이라 앞뒤 공백만 다듬고 그대로 씁니다. */
    private String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
        return name;
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static InterestResponse toResponse(InterestJpaEntity e) {
        return new InterestResponse(
                e.getId(),
                e.getName(),
                e.getDescription(),
                e.getImageUrl(),
                e.getSortOrder(),
                e.isActive());
    }
}
