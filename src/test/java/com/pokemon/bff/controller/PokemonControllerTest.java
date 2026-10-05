package com.pokemon.bff.controller;

import com.pokemon.bff.dto.*;
import com.pokemon.bff.service.PokeApiPokemonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the read-only PokeAPI controller. Status codes, headers and error bodies are covered by
 * {@link PokemonControllerWebMvcTest}.
 */
@ExtendWith(MockitoExtension.class)
class PokemonControllerTest {
    @Mock
    private PokeApiPokemonService service;

    private PokemonController controller;

    @BeforeEach
    void setUp() {
        controller = new PokemonController(service, Duration.ofMinutes(5));
    }

    @Test
    void shouldReturnPokemonPageWithPublicCaching() {
        var pokemonPage = new PokemonPage(List.of(
                new PokemonItem(1, "bulbasaur", "img-1", "Seed Pokemon", 6.9, List.of(new Skill("overgrow", "u")))),
                0, 20, 100, 5);
        when(service.findPage(0, 20)).thenReturn(pokemonPage);

        var response = controller.findAll(0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(pokemonPage, response.getBody());
        assertEquals("max-age=300, public", response.getHeaders().getCacheControl());
    }

    @Test
    void shouldMakeClientsRevalidateWhenNoMaxAgeIsConfigured() {
        var revalidating = new PokemonController(service, Duration.ZERO);
        when(service.findPage(0, 20)).thenReturn(new PokemonPage(List.of(), 0, 20, 0, 0));

        assertEquals("no-cache", revalidating.findAll(0, 20).getHeaders().getCacheControl());
    }

    @Test
    void shouldSendRetryAfterWhenEntriesWereRateLimited() {
        when(service.findPage(0, 20)).thenReturn(new PokemonPage(List.of(PokemonItem.unavailable(4, "charmander")),
                0, 20, 1, 1, 7));

        var response = controller.findAll(0, 20);

        assertEquals("7", response.getHeaders().getFirst("Retry-After"));
    }

    @Test
    void shouldNotLetPartialPagesBeStored() {
        when(service.findPage(0, 20)).thenReturn(new PokemonPage(List.of(PokemonItem.unavailable(4, "charmander")),
                0, 20, 1, 1));

        assertEquals("no-store", controller.findAll(0, 20).getHeaders().getCacheControl());
    }

    @Test
    void shouldNotLetAPageWithAMissingCategoryBeStored() {
        when(service.findPage(0, 20)).thenReturn(new PokemonPage(List.of(
                PokemonItem.withoutCategory(1, "bulbasaur", "img-1", 6.9, List.of())), 0, 20, 1, 1));

        assertEquals("no-store", controller.findAll(0, 20).getHeaders().getCacheControl());
    }

    @Test
    void shouldAlwaysRevalidateADetailWithLocalFieldsEvenWithAMaxAge() {
        var detail = new PokemonDetail(25, "pikachu", "pikachu.png", 0.4, 6.0, List.of(), "Electric mouse.",
                new EvolutionNode("pikachu", List.of()));
        when(service.findByNameOrId("pikachu")).thenReturn(detail.withMetadata(new PokemonMetadata(null, "Kanto", null)));

        assertEquals("no-cache", controller.findByNameOrId("pikachu").getHeaders().getCacheControl());
    }

    @Test
    void shouldStillNotStoreAPartialDetailWithLocalFields() {
        var partial = new PokemonDetail(25, "pikachu", "pikachu.png", 0.4, 6.0, List.of(), null,
                new EvolutionNode("pikachu", List.of()), false);
        when(service.findByNameOrId("pikachu")).thenReturn(partial.withMetadata(new PokemonMetadata(null, "Kanto", null)));

        assertEquals("no-store", controller.findByNameOrId("pikachu").getHeaders().getCacheControl());
    }

    @Test
    void shouldPropagateInvalidPaginationFromService() {
        when(service.findPage(-1, 20)).thenThrow(new IllegalArgumentException("page must be greater than or equal to 0"));

        assertThrows(IllegalArgumentException.class, () -> controller.findAll(-1, 20));
    }

    @Test
    void shouldReturnDetailedPokemon() {
        var detail = new PokemonDetail(25, "pikachu", "pikachu.png", 0.4, 6.0, List.of(), "Electric mouse.",
                new EvolutionNode("pichu", List.of(new EvolutionNode("pikachu", List.of()))));
        when(service.findByNameOrId("pikachu")).thenReturn(detail);

        var response = controller.findByNameOrId("pikachu");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(detail, response.getBody());
        assertEquals("max-age=300, public", response.getHeaders().getCacheControl());
    }

    @Test
    void shouldNotLetPartialDetailsBeStored() {
        when(service.findByNameOrId("pikachu")).thenReturn(new PokemonDetail(25, "pikachu", "pikachu.png", 0.4, 6.0,
                List.of(), null, new EvolutionNode("pikachu", List.of()), false));

        assertEquals("no-store", controller.findByNameOrId("pikachu").getHeaders().getCacheControl());
    }
}
