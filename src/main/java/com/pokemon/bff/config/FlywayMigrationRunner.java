package com.pokemon.bff.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;

/**
 * Runs Flyway migrations on startup.
 * Registered as a DatabaseInitializerDetector so JPA waits for this to finish.
 */
public class FlywayMigrationRunner implements InitializingBean {

    private final Flyway flyway;

    public FlywayMigrationRunner(Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public void afterPropertiesSet() {
        flyway.migrate();
    }
}
