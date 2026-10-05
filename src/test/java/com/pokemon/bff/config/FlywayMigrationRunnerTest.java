package com.pokemon.bff.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlywayMigrationRunnerTest {

    @Mock
    private Flyway flyway;

    @InjectMocks
    private FlywayMigrationRunner runner;

    @Test
    void shouldRunFlywayMigrateOnStartup() {
        runner.afterPropertiesSet();

        verify(flyway).migrate();
    }
}
