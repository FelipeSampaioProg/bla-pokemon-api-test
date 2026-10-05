package com.pokemon.bff.controller;

import com.pokemon.bff.dto.*;
import com.pokemon.bff.service.LocalPokemonService;
import com.pokemon.bff.service.VersionCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the local Pokemon controller's delegation. HTTP status codes and error bodies are covered by
 * {@link LocalPokemonControllerWebMvcTest}.
 */
@ExtendWith(MockitoExtension.class)
class LocalPokemonControllerTest {
    @Mock
    private LocalPokemonService service;

    @InjectMocks
    private LocalPokemonController controller;

    private static PokemonDetail response(int id, String name) {
        return new PokemonDetail(id, name, "img.png", 0.7, 6.9, List.of(), "desc", new EvolutionNode(name, List.of()));
    }

    @Test
    void shouldReturnLocalPageAskingClientsToRevalidate() {
        var page = new PokemonPage(List.of(), 1, 3, 6, 2);
        when(service.findLocalPage(1, 3)).thenReturn(page);

        var result = controller.findAll(1, 3);

        assertEquals(page, result.getBody());
        assertEquals("no-cache", result.getHeaders().getCacheControl());
    }

    @Test
    void shouldReturnLocalDetail() {
        var detail = new PokemonDetail(9001, "localmon", "local.png", 1.0, 12.5, List.of(), "Created locally",
                new EvolutionNode("localmon", List.of()));
        when(service.findLocal("localmon")).thenReturn(new Versioned<>(detail, "3-100"));

        var result = controller.findByNameOrId("localmon");

        assertEquals(detail, result.getBody());
        assertEquals("\"3-100\"", result.getHeaders().getETag());
    }

    @Test
    void shouldReturn201WithLocationWhenCreating() {
        var request = new PokemonCreateRequest(1, "bulbasaur", "img.png", 0.7, 6.9,
                "A strange seed.", List.of(), List.of(), null, null, null);
        when(service.create(request)).thenReturn(new Versioned<>(response(1, "bulbasaur"), "0-100"));

        var result = controller.createPokemon(request);

        assertEquals(201, result.getStatusCode().value());
        assertEquals(URI.create("/api/v1/local-pokemons/1"), result.getHeaders().getLocation());
        assertEquals("\"0-100\"", result.getHeaders().getETag());
        assertEquals(response(1, "bulbasaur"), result.getBody());
    }

    @Test
    void shouldRejectMissingCreateBody() {
        assertThrows(IllegalArgumentException.class, () -> controller.createPokemon(null));
        verifyNoInteractions(service);
    }

    @Test
    void shouldReplaceWithPut() {
        var request = new PokemonReplaceRequest("bulbasaur", null, null, null, null, null, null, null, null, null, null);
        // The controller reads If-Match; the service gets the version the client read.
        when(service.replace(1, VersionCondition.oneOf(List.of("1-100")), request))
                .thenReturn(new Versioned<>(response(1, "bulbasaur"), "3-100"));

        var result = controller.replacePokemon(1, "\"1-100\"", request);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("\"3-100\"", result.getHeaders().getETag());
        verify(service, never()).update(any(), any(), any());
    }

    @Test
    void shouldUpdateWithPatch() {
        var request = new PokemonUpdateRequest("bulbasaur-updated", null, null, null, null,
                List.of(), List.of(), null, null, null);
        when(service.update(1, null, request)).thenReturn(new Versioned<>(response(1, "bulbasaur-updated"), "2-100"));

        var result = controller.updatePokemon(1, null, request);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("bulbasaur-updated", result.getBody().name());
        assertEquals("\"2-100\"", result.getHeaders().getETag());
        verify(service, never()).replace(any(), any(), any());
    }

    @Test
    void shouldPropagateNotFoundOnUpdate() {
        when(service.update(eq(999), any(), any())).thenThrow(new NoSuchElementException("Not found"));

        assertThrows(NoSuchElementException.class,
                () -> controller.updatePokemon(999, null, new PokemonUpdateRequest("ghost", null, null, null, null, null)));
    }

    @Test
    void shouldReturn204WhenDeleteIsSuccessful() {
        var result = controller.deletePokemon(1, "\"4-100\"");

        assertEquals(204, result.getStatusCode().value());
        verify(service).delete(1, VersionCondition.oneOf(List.of("4-100")));
    }

    @Test
    void shouldPropagateNotFoundOnDelete() {
        doThrow(new NoSuchElementException("Not found")).when(service).delete(999, null);

        assertThrows(NoSuchElementException.class, () -> controller.deletePokemon(999, null));
    }
}
