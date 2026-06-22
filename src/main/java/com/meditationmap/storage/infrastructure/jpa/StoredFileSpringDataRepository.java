package com.meditationmap.storage.infrastructure.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileSpringDataRepository extends JpaRepository<StoredFileJpaEntity, String> {

    Optional<StoredFileJpaEntity> findByObjectKey(String objectKey);
}
