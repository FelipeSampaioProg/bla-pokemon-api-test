package com.pokemon.bff.sync;

import com.pokemon.bff.config.PokemonAsyncConfiguration;
import com.pokemon.bff.persistence.repository.PokemonRepository;
import com.pokemon.bff.persistence.repository.PokemonTombstoneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PokemonSyncServiceAsyncTest {

    @Test
    void shouldSyncOnDedicatedThreadWithoutBlockingCaller() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(TestAsyncConfiguration.class)) {
            PokemonSyncService syncService = context.getBean(PokemonSyncService.class);
            PokemonRepository repository = context.getBean(PokemonRepository.class);
            var release = new CountDownLatch(1);
            var syncThread = new CompletableFuture<String>();
            when(repository.findById(4)).thenAnswer(inv -> {
                syncThread.complete(Thread.currentThread().getName());
                release.await(5, TimeUnit.SECONDS);
                return Optional.empty();
            });
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Returns while the database work is still blocked on the latch.
            syncService.sync(charmander());

            assertTrue(syncThread.get(5, TimeUnit.SECONDS).startsWith("pokemon-sync-"));
            verify(repository, never()).save(any());
            release.countDown();
            verify(repository, timeout(5000)).save(any());
        }
    }

    @Test
    void shouldNotPropagateDatabaseFailureToCaller() {
        try (var context = new AnnotationConfigApplicationContext(TestAsyncConfiguration.class)) {
            PokemonSyncService syncService = context.getBean(PokemonSyncService.class);
            PokemonRepository repository = context.getBean(PokemonRepository.class);
            when(repository.findById(4)).thenThrow(new IllegalStateException("database down"));

            assertDoesNotThrow(() -> syncService.sync(charmander()));
            verify(repository, timeout(5000)).findById(4);
            assertEquals(0, mockingDetails(repository).getInvocations().stream()
                    .filter(invocation -> invocation.getMethod().getName().equals("save")).count());
        }
    }

    private static PokemonSnapshot charmander() {
        return new PokemonSnapshot(4, "charmander", "img.png", 0.6, 8.5, "Lizard Pokemon", List.of(), List.of(),
                null, true);
    }

    @Configuration
    @Import(PokemonAsyncConfiguration.class)
    static class TestAsyncConfiguration {
        @Bean
        PokemonRepository pokemonRepository() {
            return mock(PokemonRepository.class);
        }

        @Bean
        PokemonSyncService pokemonSyncService(PokemonRepository pokemonRepository) {
            return new PokemonSyncService(pokemonRepository, mock(PokemonTombstoneRepository.class));
        }
    }
}
