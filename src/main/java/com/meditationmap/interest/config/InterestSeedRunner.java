package com.meditationmap.interest.config;

import com.meditationmap.interest.infrastructure.jpa.InterestJpaEntity;
import com.meditationmap.interest.infrastructure.jpa.InterestSpringDataRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 표가 비어 있을 때만 기본 관심사를 넣습니다.
 *
 * <p>매번 코드 값으로 되돌리지 않습니다. 이 목록은 관리자가 고치라고 만든 것이라, 관리자가
 * 지우거나 이름을 바꾼 항목을 기동할 때마다 되살리면 안 됩니다.
 *
 * <p>사진은 넣지 않습니다. 관리자가 올릴 자리이고, 없으면 프런트가 색 카드로 그립니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterestSeedRunner implements ApplicationRunner {

    private record Seed(String name, String description) {}

    private static final List<Seed> DEFAULTS =
            List.of(
                    new Seed("마음챙김", "지금 이 순간에 머무는 연습으로 하루의 속도를 늦춥니다."),
                    new Seed("숲 명상", "나무 사이를 천천히 걸으며 감각을 여는 시간입니다."),
                    new Seed("호흡명상", "숨을 세고 따라가며 흐트러진 마음을 가라앉힙니다."),
                    new Seed("걷기명상", "발바닥에 닿는 감각에 집중하며 걷습니다."),
                    new Seed("소리명상", "싱잉볼과 자연의 소리에 몸을 맡깁니다."),
                    new Seed("아트명상", "그리고 만드는 동안 생각이 저절로 가라앉습니다."),
                    new Seed("힐링명상", "지친 몸과 마음을 회복하는 데 초점을 둡니다."),
                    new Seed("템플스테이", "산사에 머물며 일상과 거리를 둡니다."),
                    new Seed("숙박 프로그램", "하루 이상 머무르며 깊이 들어가는 과정입니다."),
                    new Seed("행사", "계절과 절기에 맞춰 열리는 특별한 자리입니다."));

    private final InterestSpringDataRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        for (int i = 0; i < DEFAULTS.size(); i++) {
            Seed seed = DEFAULTS.get(i);
            InterestJpaEntity entity = new InterestJpaEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setName(seed.name());
            entity.setDescription(seed.description());
            entity.setSortOrder(i);
            entity.setActive(true);
            repository.save(entity);
        }
        log.info("관심사 기본값 {}건 생성", DEFAULTS.size());
    }
}
