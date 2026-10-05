package com.pokemon.bff.service;

import com.pokemon.bff.client.PokeApiNotFoundException;
import com.pokemon.bff.client.PokeApiRateLimitException;
import com.pokemon.bff.client.PokeApiRetrier;
import com.pokemon.bff.client.PokeApiStatus;
import com.pokemon.bff.client.PokemonClient;
import com.pokemon.bff.client.dto.PokemonDetailsResponse;
import com.pokemon.bff.client.dto.PokemonListResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the access to PokeAPI (no Spring proxy here, so no caching:
 * {@code PokeApiPokemonServiceCachingTest} covers that): key normalization, the health record of each call and how failures reach the layers above.
 */
@ExtendWith(MockitoExtension.class)
class PokeApiCacheTest {
    @Mock
    private PokemonClient client;

    private PokeApiStatus status;
    private PokeApiCache pokeApi;

    @BeforeEach
    void setUp() {
        status = new PokeApiStatus();
        pokeApi = new PokeApiCache(client, status, PokeApiRetrier.none());
    }

    private static FeignException upstream(int code) {
        var request = Request.create(Request.HttpMethod.GET, "https://pokeapi.co/api/v2/pokemon/x", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("PokemonClient#find",
                Response.builder().status(code).reason("x").request(request).headers(Map.of()).build());
    }

    @Test
    void shouldAskPokeApiWithTheNormalizedNameAndRecordTheSuccess() {
        var pikachu = new PokemonDetailsResponse(25, "pikachu", 4, 60, null, List.of(), List.of());
        when(client.findByNameOrId("pikachu")).thenReturn(pikachu);

        assertSame(pikachu, pokeApi.pokemon(" Pikachu ", true));
        assertNotNull(status.lastSuccess());
        assertNull(status.lastFailure());
    }

    @Test
    void shouldReportANotFoundWithoutTheHttpLibraryAndCountItAsAnAnswer() {
        when(client.findByNameOrId("missingno")).thenThrow(upstream(404));

        var error = assertThrows(PokeApiNotFoundException.class, () -> pokeApi.pokemon("missingno", true));

        assertInstanceOf(FeignException.NotFound.class, error.getCause());
        assertNull(status.lastFailure(), "PokeAPI answered: a 404 is not an outage");
    }

    @Test
    void shouldRecordAnUpstreamFailureForTheHealthCheck() {
        when(client.findSpeciesByNameOrId("pikachu")).thenThrow(upstream(500));

        assertThrows(FeignException.class, () -> pokeApi.species("pikachu", false));
        assertNotNull(status.lastFailure());
    }

    @Test
    void shouldNotTreatARateLimitAsAnOutage() {
        var until = Instant.now().plusSeconds(30);
        when(client.findEvolutionChainById(1)).thenThrow(new PokeApiRateLimitException(until, true, null));

        assertThrows(PokeApiRateLimitException.class, () -> pokeApi.evolutionChain(1));
        assertNull(status.lastFailure());
    }

    @Test
    void shouldRefuseAnEmptyReferenceList() {
        when(client.findAll(0, 20)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> pokeApi.references(0, 20));
    }

    @Test
    void shouldReturnTheReferenceList() {
        var list = new PokemonListResponse(1, List.of());
        when(client.findAll(20, 20)).thenReturn(list);

        assertSame(list, pokeApi.references(20, 20));
        assertEquals("work", pokeApi.withinRequestBudget(() -> "work"));
    }

    @Test
    void shouldNormalizeCacheKeys() {
        assertEquals("pikachu", PokemonCacheKeys.pokemon("  PIKACHU "));
        assertEquals("", PokemonCacheKeys.pokemon(null));
        assertEquals("40:20", PokemonCacheKeys.references(40, 20));
    }
}
