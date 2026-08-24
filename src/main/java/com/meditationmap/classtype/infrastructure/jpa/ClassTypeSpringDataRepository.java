package com.meditationmap.classtype.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassTypeSpringDataRepository extends JpaRepository<ClassTypeJpaEntity, String> {

    List<ClassTypeJpaEntity> findAllByOrderBySortOrderAscNameAsc();

    List<ClassTypeJpaEntity> findAllByActiveTrueOrderBySortOrderAscNameAsc();

    Optional<ClassTypeJpaEntity> findByName(String name);
}
