package com.meditationmap.classtype.config;

import com.meditationmap.classtype.infrastructure.jpa.ClassTypeJpaEntity;
import com.meditationmap.classtype.infrastructure.jpa.ClassTypeSpringDataRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 표가 비어 있을 때만 기본 클래스 종류를 넣습니다.
 *
 * <p>지역 시더와 달리 매번 코드 값으로 되돌리지 않습니다. 이 목록은 관리자가 고치라고 만든
 * 것이라, 관리자가 지운 항목을 기동할 때마다 되살리면 안 됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassTypeSeedRunner implements ApplicationRunner {

    private static final List<String> DEFAULTS =
            List.of("마음챙김", "아트명상", "숲 명상", "호흡명상", "걷기명상", "소리명상");

    private final ClassTypeSpringDataRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        for (int i = 0; i < DEFAULTS.size(); i++) {
            ClassTypeJpaEntity entity = new ClassTypeJpaEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setName(DEFAULTS.get(i));
            entity.setSortOrder(i);
            entity.setActive(true);
            repository.save(entity);
        }
        log.info("클래스 종류 기본값 {}건 생성", DEFAULTS.size());
    }
}
