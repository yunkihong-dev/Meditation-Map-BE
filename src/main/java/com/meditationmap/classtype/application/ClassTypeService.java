package com.meditationmap.classtype.application;

import com.meditationmap.classtype.infrastructure.jpa.ClassTypeJpaEntity;
import com.meditationmap.classtype.infrastructure.jpa.ClassTypeSpringDataRepository;
import com.meditationmap.classtype.presentation.dto.ClassTypeResponse;
import com.meditationmap.classtype.presentation.dto.ClassTypeUpsertRequest;
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
public class ClassTypeService {

    private final ClassTypeSpringDataRepository repository;

    /** 전문가 화면용 — 내려둔 항목은 빼고 줍니다. */
    public List<ClassTypeResponse> listActive() {
        return repository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(ClassTypeService::toResponse)
                .toList();
    }

    /** 관리자 화면용 — 내려둔 항목까지 전부 줍니다. */
    public List<ClassTypeResponse> listAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(ClassTypeService::toResponse)
                .toList();
    }

    @Transactional
    public ClassTypeResponse create(ClassTypeUpsertRequest request) {
        String name = normalizeName(request.name());
        rejectDuplicate(name, null);

        ClassTypeJpaEntity entity = new ClassTypeJpaEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(name);
        entity.setActive(request.active() == null || request.active());
        entity.setSortOrder(request.sortOrder() != null ? request.sortOrder() : nextSortOrder());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public ClassTypeResponse update(String id, ClassTypeUpsertRequest request) {
        ClassTypeJpaEntity entity =
                repository
                        .findById(id)
                        .orElseThrow(() -> new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND));
        String name = normalizeName(request.name());
        rejectDuplicate(name, id);

        entity.setName(name);
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
        Optional<ClassTypeJpaEntity> existing = repository.findByName(name);
        if (existing.isPresent() && !existing.get().getId().equals(allowedId)) {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
    }

    private int nextSortOrder() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream()
                        .mapToInt(ClassTypeJpaEntity::getSortOrder)
                        .max()
                        .orElse(-1)
                + 1;
    }

    /** 이름이 곧 전문가 프로필에 저장되는 값이라 앞뒤 공백만 다듬고 그대로 씁니다. */
    private String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new DomainArgumentException(ErrorCode.INVALID_PARAMETER);
        }
        return name;
    }

    private static ClassTypeResponse toResponse(ClassTypeJpaEntity e) {
        return new ClassTypeResponse(e.getId(), e.getName(), e.isActive());
    }
}
