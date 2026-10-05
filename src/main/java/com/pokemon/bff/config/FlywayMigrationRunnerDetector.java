package com.pokemon.bff.config;

import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;

import java.util.Set;

/**
 * Registers FlywayMigrationRunner as a database initializer so that JPA's
 * EntityManagerFactory waits for Flyway to complete before validating the schema.
 */
public class FlywayMigrationRunnerDetector extends AbstractBeansOfTypeDatabaseInitializerDetector {

    @Override
    protected Set<Class<?>> getDatabaseInitializerBeanTypes() {
        return Set.of(FlywayMigrationRunner.class);
    }
}
