package com.meditationmap.platform.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * Spring Boot 는 `.env` 를 자동 로드하지 않는다. 기동 최초에 현재 디렉터리(또는 {@code ENV_FILE_PATH}) 의
 * `.env` 를 시스템 프로퍼티로 올린다 — 부모에게 이미 {@code getenv} 된 키는 덮어쓰지 않음(배포/도커/CI 우선).
 */
@Slf4j
public final class DotEnvBootstrap {

    private DotEnvBootstrap() {}

    public static void loadIfPresent() {
        Path envPath = resolveEnvFilePath();
        if (envPath == null || !Files.isRegularFile(envPath)) {
            return;
        }
        try {
            Dotenv dotenv =
                    Dotenv.configure()
                            .ignoreIfMalformed()
                            .directory(envPath.getParent().toString())
                            .filename(envPath.getFileName().toString())
                            .load();
            int applied = 0;
            for (DotenvEntry e : dotenv.entries()) {
                String key = e.getKey();
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                if (System.getenv(key) != null) {
                    continue;
                }
                if (System.getProperty(key) != null) {
                    continue;
                }
                String val = e.getValue() != null ? e.getValue() : "";
                System.setProperty(key, val);
                applied++;
            }
            log.info(
                    ".env 로드됨 경로={}, 시스템 프로퍼티로 반영된 항목 수={}",
                    envPath.toAbsolutePath(),
                    applied);
        } catch (RuntimeException ex) {
            log.warn(".env 로드 실패 path={}, 이유={}", envPath.toAbsolutePath(), ex.getMessage());
        }
    }

    /** {@code ENV_FILE_PATH} 또는 {@code user.dir/.env}. */
    private static Path resolveEnvFilePath() {
        String explicit = System.getenv("ENV_FILE_PATH");
        if (StringUtils.hasText(explicit)) {
            return Paths.get(explicit.trim());
        }
        return Paths.get(System.getProperty("user.dir", "."), ".env").normalize();
    }
}
