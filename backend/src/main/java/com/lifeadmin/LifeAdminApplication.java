package com.lifeadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Life Admin backend entry point.
 *
 * <p>{@link EnableJpaAuditing} powers the {@code createdAt}/{@code updatedAt} columns on the
 * {@code Auditable} mapped superclass. {@link EnableScheduling} is enabled now so the reminder
 * scheduler (Phase 4) can hook in without a config change; it is harmless with no scheduled beans.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class LifeAdminApplication {

    public static void main(final String[] args) {
        SpringApplication.run(LifeAdminApplication.class, args);
    }
}
