package com.meditationmap.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * `@WebMvcTest` 슬라이스가 쓰는 <b>테스트 전용</b> 부트 설정.
 *
 * <p>기본값대로 두면 슬라이스가 `MeditationMapApplication` 을 설정 클래스로 잡는데, 그 클래스에
 * 붙은 `@EnableJpaRepositories` 가 슬라이스 안에서 JPA 리포지토리 빈을 등록하려 들고
 * `entityManagerFactory` 가 없어 컨텍스트 로딩이 실패한다(`NoSuchBeanDefinitionException:
 * No bean named 'entityManagerFactory' available`). `@EnableCaching` 도 같은 이유로 슬라이스에는
 * 불필요하다.
 *
 * <p>그래서 컴포넌트 스캔만 남긴 설정을 따로 두고 컨트롤러 테스트가 `@ContextConfiguration` 으로
 * 이 클래스를 직접 지정한다. 스캔 결과는 `@WebMvcTest(controllers = ...)` 가 거는
 * `WebMvcTypeExcludeFilter` 가 다시 좁히므로 지정한 컨트롤러만 올라온다.
 *
 * <p><b>주의</b>: 이 설정에는 앱의 `SecurityConfig` 가 들어 있지 않다. 따라서 이 설정을 쓰는
 * 테스트는 엔드포인트의 공개/비공개 여부를 검증할 수 없다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = "com.meditationmap",
        // `@SpringBootApplication` 이 기본으로 달고 있는 두 필터다. 빼면 평범한 컴포넌트 스캔이 되어
        // `@Service`·`@Repository` 는 물론 `MeditationMapApplication` 자신까지 빈으로 올라오고,
        // 그 클래스의 `@EnableJpaRepositories` 가 다시 살아나 슬라이스가 깨진다.
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(
                    type = FilterType.CUSTOM,
                    classes = AutoConfigurationExcludeFilter.class)
        })
public class WebSliceBootConfiguration {}
