package com.trekking.app;

import java.nio.file.Path;
import java.time.Duration;
import java.util.TimeZone;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public abstract class AuthTestInfrastructure {
    static final String JWT_SECRET = "auth-test-jwt-secret-for-tests-only-123456789";
    static final String OTP_SECRET = "auth-test-otp-secret-for-tests-only-987654321";
    static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:18.6"))
            .withEnv("POSTGRES_USER", "auth_test").withEnv("POSTGRES_PASSWORD", "auth_test")
            .withEnv("POSTGRES_DB", "auth_test").withExposedPorts(5432)
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/schema.sql")), "/docker-entrypoint-initdb.d/schema.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/001_add_google_subject.sql")),
                    "/docker-entrypoint-initdb.d/zz_google_auth.sql")
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
            .withStartupTimeout(Duration.ofMinutes(2));
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.2-alpine"))
            .withExposedPorts(6379);

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/auth_test?currentSchema=trekking");
        registry.add("spring.datasource.username", () -> "auth_test");
        registry.add("spring.datasource.password", () -> "auth_test");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("app.auth.jwt-secret", () -> JWT_SECRET);
        registry.add("app.auth.otp-secret", () -> OTP_SECRET);
        registry.add("app.auth.cookie-secure", () -> false);
        registry.add("app.auth.allowed-origins", () -> "http://localhost:5173");
        registry.add("app.auth.redis-prefix", () -> "auth-test:");
        registry.add("app.auth.mail-from", () -> "test@example.invalid");
        registry.add("app.auth.google.client-ids", () -> "");
    }
}
