package com.meditationmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableCaching
@SpringBootApplication
@EntityScan(basePackages = "com.meditationmap")
@EnableJpaRepositories(basePackages = "com.meditationmap")
public class MeditationMapApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeditationMapApplication.class, args);
    }
}
