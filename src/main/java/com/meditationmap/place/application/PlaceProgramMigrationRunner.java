package com.meditationmap.place.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.place.infrastructure.jpa.PlaceSpringDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기존 places.data JSON의 programs[]에 startDate/endDate·status(기간 기준)를 반영합니다.
 * JSON blob 스키마 변경은 places.data 내부 programs 항목 필드 추가로 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceProgramMigrationRunner implements ApplicationRunner {

    private final PlaceSpringDataRepository placeRepo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = 0;
        for (PlaceJpaEntity entity : placeRepo.findAll()) {
            if (entity.getData() == null || !entity.getData().isObject()) {
                continue;
            }
            ObjectNode before = (ObjectNode) entity.getData().deepCopy();
            ObjectNode after = PlaceProgramNormalizer.normalizePlaceData(before);
            if (!after.equals(before)) {
                entity.setData(after);
                placeRepo.save(entity);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Place programs normalized (period/status): {} place(s) updated", updated);
        }
    }
}
