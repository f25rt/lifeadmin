package com.lifeadmin.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests needing a real PostgreSQL.
 *
 * <p>Uses the Testcontainers <em>singleton container</em> pattern: one Postgres container is started
 * once for the whole test JVM and never explicitly stopped (Ryuk reaps it on exit). This avoids the
 * per-class start/stop lifecycle tearing the DB out from under a later class's cached Spring context
 * when the full suite runs in one JVM, and makes the suite faster (later classes reuse the migrated
 * container). Flyway migrates it; Hibernate {@code validate} confirms schema alignment.
 */
@SpringBootTest
@Import(InMemoryObjectStorageProvider.class)
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("lifeadmin_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // JWT secret long enough for HS256 in the test context.
        registry.add("lifeadmin.jwt.secret", () -> "integration-test-secret-that-is-long-enough-32b+!");
        // Disable the S3 storage provider in tests (no MinIO container); the in-memory stub
        // (InMemoryObjectStorageProvider) is imported by tests that need storage.
        registry.add("lifeadmin.storage.enabled", () -> "false");
        // Process documents synchronously in tests (no RabbitMQ broker): the synchronous publisher
        // runs the pipeline inline on upload.
        registry.add("lifeadmin.messaging.enabled", () -> "false");
        // No SMTP server in tests: use the logging email provider.
        registry.add("lifeadmin.email.provider", () -> "log");
        // The suite fires many auth logins per class; keep the fixed-window rate limiter off so it
        // never trips. A dedicated test exercises the limiter with it enabled.
        registry.add("lifeadmin.rate-limit.enabled", () -> "false");
        // Never let the @Scheduled reminder poll fire on its own during tests — it would race with
        // the manual scheduler.poll() calls and make notification counts nondeterministic. Tests
        // drive the scheduler explicitly. (~1 year interval effectively disables the auto-run.)
        registry.add("lifeadmin.reminder.poll-interval-ms", () -> "31536000000");
    }
}
