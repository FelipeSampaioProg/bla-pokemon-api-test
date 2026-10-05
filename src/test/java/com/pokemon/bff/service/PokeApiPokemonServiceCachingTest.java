package com.pokemon.bff.service;

import com.pokemon.bff.client.PokemonClient;
import com.pokemon.bff.client.dto.*;
import com.pokemon.bff.config.PokemonCacheNames;
import com.pokemon.bff.persistence.repository.PokemonRepository;
import com.pokemon.bff.persistence.repository.PokemonTombstoneRepository;
import com.pokemon.bff.sync.PokemonSyncService;
import com.pokemon.bff.sync.PokemonSyncThrottle;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PokeApiPokemonServiceCachingTest {

    @Test
    void shouldCacheDetailLookupsUsingNormalizedPokemonKey() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestCachingConfiguration.class)) {
            PokeApiPokemonService service = context.getBean(PokeApiPokemonService.class);
            PokemonClient client = context.getBean(PokemonClient.class);
            PokemonSyncService syncService = context.getBean(PokemonSyncService.class);

            when(client.findByNameOrId("pikachu")).thenReturn(new PokemonDetailsResponse(
                    25, "pikachu", 4, 60, new PokemonDetailsResponse.Sprites("img"),
                    List.of(), List.of()));
            when(client.findSpeciesByNameOrId("pikachu")).thenReturn(new PokemonSpeciesResponse(
                    List.of(),
                    List.of(new PokemonSpeciesResponse.FlavorTextEntry("Electric mouse",
                            new PokemonSpeciesResponse.Language("en"))),
                    new PokemonSpeciesResponse.EvolutionChain("https://pokeapi.co/api/v2/evolution-chain/10/")));
            when(client.findEvolutionChainById(10)).thenReturn(new EvolutionChainResponse(
                    new EvolutionChainResponse.ChainLink(new PokemonReference("pikachu", "u"), List.of())));

            var first = service.findByNameOrId(" Pikachu ");
            var second = service.findByNameOrId("pikachu");

            assertEquals(first, second);
            verify(client, times(1)).findByNameOrId("pikachu");
            verify(client, times(1)).findSpeciesByNameOrId("pikachu");
            verify(client, times(1)).findEvolutionChainById(10);
            // the second view finds the sync mark of the first one: the local store is written once per TTL
            verify(syncService, times(1)).sync(any());
        }
    }

    @Test
    void shouldReuseTheCardResourcesWhenOpeningItsDetail() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestCachingConfiguration.class)) {
            PokeApiPokemonService service = context.getBean(PokeApiPokemonService.class);
            PokemonClient client = context.getBean(PokemonClient.class);

            when(client.findAll(0, 1)).thenReturn(new PokemonListResponse(1,
                    List.of(new PokemonReference("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"))));
            stubPokemon(client, 1, "bulbasaur");
            when(client.findEvolutionChainById(1)).thenReturn(new EvolutionChainResponse(
                    new EvolutionChainResponse.ChainLink(new PokemonReference("bulbasaur", "u"), List.of())));

            var card = service.findPage(0, 1).content().getFirst();
            var detail = service.findByNameOrId(card.name());

            // the detail only adds the evolution chain call; pokemon and species come from the card's cache
            verify(client, times(1)).findByNameOrId("bulbasaur");
            verify(client, times(1)).findSpeciesByNameOrId("bulbasaur");
            verify(client, times(1)).findEvolutionChainById(1);
            // and both show the same data
            assertEquals(card.image(), detail.image());
            assertEquals(card.category(), detail.category());
            assertEquals(card.mass(), detail.mass());
            assertEquals(card.skills(), detail.skills());
            // listing and detail store the same data, so the Pokemon is synchronised once, not once per story
            verify(context.getBean(PokemonSyncService.class), times(1)).sync(any());
        }
    }

    @Test
    void shouldNotCacheDetailWhoseEvolutionChainFailed() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestCachingConfiguration.class)) {
            PokeApiPokemonService service = context.getBean(PokeApiPokemonService.class);
            PokemonClient client = context.getBean(PokemonClient.class);

            when(client.findByNameOrId("pikachu")).thenReturn(new PokemonDetailsResponse(
                    25, "pikachu", 4, 60, new PokemonDetailsResponse.Sprites("img"), List.of(), List.of()));
            when(client.findSpeciesByNameOrId("pikachu")).thenReturn(new PokemonSpeciesResponse(
                    List.of(), List.of(),
                    new PokemonSpeciesResponse.EvolutionChain("https://pokeapi.co/api/v2/evolution-chain/10/")));
            when(client.findEvolutionChainById(10))
                    .thenThrow(new RuntimeException("upstream 503"))
                    .thenReturn(new EvolutionChainResponse(
                            new EvolutionChainResponse.ChainLink(new PokemonReference("pichu", "u"), List.of())));

            var degraded = service.findByNameOrId("pikachu");
            var recovered = service.findByNameOrId("pikachu");
            var cached = service.findByNameOrId("pikachu");

            assertFalse(degraded.evolutionAvailable());
            assertTrue(recovered.evolutionAvailable());
            assertEquals("pichu", recovered.evolutionLineage().name());
            assertEquals(recovered, cached);
            verify(client, times(2)).findEvolutionChainById(10);
            verify(client, times(1)).findByNameOrId("pikachu");
        }
    }

    @Test
    void shouldNotCacheDetailWhoseSpeciesFailed() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestCachingConfiguration.class)) {
            PokeApiPokemonService service = context.getBean(PokeApiPokemonService.class);
            PokemonClient client = context.getBean(PokemonClient.class);

            when(client.findByNameOrId("pikachu")).thenReturn(new PokemonDetailsResponse(
                    25, "pikachu", 4, 60, new PokemonDetailsResponse.Sprites("img"), List.of(), List.of()));
            when(client.findSpeciesByNameOrId("pikachu"))
                    .thenThrow(new RuntimeException("upstream 503"))
                    .thenReturn(new PokemonSpeciesResponse(List.of(),
                            List.of(new PokemonSpeciesResponse.FlavorTextEntry("Electric mouse.",
                                    new PokemonSpeciesResponse.Language("en"))),
                            null));

            var degraded = service.findByNameOrId("pikachu");
            var recovered = service.findByNameOrId("pikachu");

            assertFalse(degraded.descriptionAvailable());
            assertTrue(recovered.descriptionAvailable());
            assertEquals("Electric mouse.", recovered.description());
            // only the resource that failed is requested again; the Pokemon itself was cached
            verify(client, times(1)).findByNameOrId("pikachu");
            verify(client, times(2)).findSpeciesByNameOrId("pikachu");
        }
    }

    @Test
    void shouldCacheListReferencesAndItemsAcrossOverlappingPages() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestCachingConfiguration.class)) {
            PokeApiPokemonService service = context.getBean(PokeApiPokemonService.class);
            PokemonClient client = context.getBean(PokemonClient.class);
            PokemonSyncService syncService = context.getBean(PokemonSyncService.class);

            when(client.findAll(0, 2)).thenReturn(new PokemonListResponse(3, List.of(
                    new PokemonReference("bulbasaur", "u1"), new PokemonReference("ivysaur", "u2"))));
            when(client.findAll(1, 1)).thenReturn(new PokemonListResponse(3, List.of(
                    new PokemonReference("ivysaur", "u2"))));
            stubPokemon(client, 1, "bulbasaur");
            stubPokemon(client, 2, "ivysaur");

            var first = service.findPage(0, 2);
            var second = service.findPage(0, 2);
            var overlapping = service.findPage(1, 1);

            assertEquals(first, second);
            assertEquals(first.content().get(1), overlapping.content().getFirst());
            verify(client, times(1)).findAll(0, 2);
            verify(client, times(1)).findAll(1, 1);
            verify(client, times(1)).findByNameOrId("bulbasaur");
            verify(client, times(1)).findByNameOrId("ivysaur");
            verify(client, times(1)).findSpeciesByNameOrId("ivysaur");
            verify(syncService, times(2)).sync(any());
        }
    }

    @Test
    void shouldNotCacheItemsThatFailedUpstream() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestCachingConfiguration.class)) {
            PokeApiPokemonService service = context.getBean(PokeApiPokemonService.class);
            PokemonClient client = context.getBean(PokemonClient.class);

            when(client.findAll(0, 1)).thenReturn(new PokemonListResponse(1,
                    List.of(new PokemonReference("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"))));
            when(client.findByNameOrId("bulbasaur"))
                    .thenThrow(new RuntimeException("upstream 503"))
                    .thenReturn(details(1, "bulbasaur"));
            when(client.findSpeciesByNameOrId("bulbasaur")).thenReturn(species());

            var degraded = service.findPage(0, 1);
            var recovered = service.findPage(0, 1);

            assertFalse(degraded.content().getFirst().available());
            assertTrue(recovered.content().getFirst().available());
            assertEquals("Seed Pokemon", recovered.content().getFirst().category());
        }
    }

    private static void stubPokemon(PokemonClient client, int id, String name) {
        when(client.findByNameOrId(name)).thenReturn(details(id, name));
        when(client.findSpeciesByNameOrId(name)).thenReturn(species());
    }

    private static PokemonDetailsResponse details(int id, String name) {
        return new PokemonDetailsResponse(id, name, 7, 69, new PokemonDetailsResponse.Sprites("img-" + id),
                List.of(new PokemonDetailsResponse.AbilitySlot(
                        new PokemonDetailsResponse.Ability("overgrow", "u-ability"))),
                List.of());
    }

    private static PokemonSpeciesResponse species() {
        return new PokemonSpeciesResponse(
                List.of(new PokemonSpeciesResponse.Genus("Seed Pokemon",
                        new PokemonSpeciesResponse.Language("en"))),
                List.of(),
                new PokemonSpeciesResponse.EvolutionChain("https://pokeapi.co/api/v2/evolution-chain/1/"));
    }

    @Configuration
    @EnableCaching
    static class TestCachingConfiguration {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(PokemonCacheNames.POKEMON_PAGE, PokemonCacheNames.POKEAPI_POKEMON,
                    PokemonCacheNames.POKEAPI_SPECIES, PokemonCacheNames.POKEAPI_EVOLUTION,
                    PokemonCacheNames.POKEMON_SYNC);
        }

        @Bean
        PokemonClient pokemonClient() {
            return mock(PokemonClient.class);
        }

        @Bean
        PokemonSyncService pokemonSyncService() {
            return mock(PokemonSyncService.class);
        }

        @Bean
        PokemonRepository pokemonRepository() {
            return mock(PokemonRepository.class);
        }

        @Bean
        PokeApiCache pokeApiCache(PokemonClient pokemonClient) {
            return new PokeApiCache(pokemonClient);
        }

        @Bean
        PokemonSyncThrottle pokemonSyncThrottle(CacheManager cacheManager) {
            return new PokemonSyncThrottle(cacheManager);
        }

        @Bean
        PokemonCatalogService pokemonCatalogService(PokeApiCache pokeApiCache, PokemonSyncService pokemonSyncService,
                PokemonSyncThrottle pokemonSyncThrottle) {
            return new PokemonCatalogService(pokeApiCache, pokemonSyncService, pokemonSyncThrottle);
        }

        @Bean(destroyMethod = "shutdown")
        ExecutorService pokemonFetchExecutor() {
            return Executors.newFixedThreadPool(4);
        }

        @Bean
        PokeApiPokemonService pokemonService(PokeApiCache pokeApiCache, PokemonSyncService pokemonSyncService,
                PokemonSyncThrottle pokemonSyncThrottle, PokemonRepository pokemonRepository,
                PokemonCatalogService pokemonCatalogService, ExecutorService pokemonFetchExecutor) {
            return new PokeApiPokemonService(pokeApiCache, pokemonSyncService, pokemonSyncThrottle, pokemonRepository,
                    pokemonCatalogService, pokemonFetchExecutor);
        }
    }
}
