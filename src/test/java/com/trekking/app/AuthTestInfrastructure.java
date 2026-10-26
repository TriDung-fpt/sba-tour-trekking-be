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
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/002_add_account_session_version.sql")),
                    "/docker-entrypoint-initdb.d/zzz_account_session_version.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/003_add_cooperation_policy_terms.sql")),
                    "/docker-entrypoint-initdb.d/zzzz_policy_agreement_terms.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/004_add_financial_permissions.sql")),
                    "/docker-entrypoint-initdb.d/zzzzz_financial_permissions.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/005_single_operator_foundation.sql")),
                    "/docker-entrypoint-initdb.d/zzzzzz_single_operator_foundation.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/006_remove_company_domain.sql")),
                    "/docker-entrypoint-initdb.d/zzzzzzz_remove_company_domain.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/007_add_tour_equipment_catalog.sql")),
                    "/docker-entrypoint-initdb.d/zzzzzzzz_add_tour_equipment_catalog.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/008_add_tour_cloudinary_public_id.sql")),
                    "/docker-entrypoint-initdb.d/zzzzzzzzz_add_tour_cloudinary_public_id.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/009_add_rental_package_templates.sql")),
                    "/docker-entrypoint-initdb.d/zzzzzzzzzz_add_rental_package_templates.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/010_abstract_tour_media_storage_key.sql")),
                    "/docker-entrypoint-initdb.d/zzzzzzzzzzz_abstract_tour_media_storage_key.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/011_add_equipment_catalog_images.sql")),
                    "/docker-entrypoint-initdb.d/zzzzzzzzzzzz_add_equipment_catalog_images.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/012_define_rental_price_units.sql")),
                    "/docker-entrypoint-initdb.d/zzzzzzzzzzzzz_define_rental_price_units.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("schema/migrations/013_system_settings.sql")),
                    "/docker-entrypoint-initdb.d/zzzzzzzzzzzzzz_system_settings.sql")
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
        // SQL migrations must produce the complete schema; Hibernate must not repair missing columns or constraints.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
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
