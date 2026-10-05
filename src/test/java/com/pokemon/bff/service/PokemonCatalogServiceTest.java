package com.pokemon.bff.service;

import com.pokemon.bff.client.PokeApiRateLimitException;
import com.pokemon.bff.client.PokemonClient;
import com.pokemon.bff.client.dto.PokemonDetailsResponse;
import com.pokemon.bff.client.dto.PokemonListResponse;
import com.pokemon.bff.client.dto.PokemonReference;
import com.pokemon.bff.client.dto.PokemonSpeciesResponse;
import com.pokemon.bff.dto.PokemonItem;
import com.pokemon.bff.sync.PokemonSnapshot;
import com.pokemon.bff.sync.PokemonSyncService;
import com.pokemon.bff.sync.PokemonSyncThrottle;
import org.springframework.cache.support.NoOpCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PokemonCatalogServiceTest {

    @Mock
    private PokemonClient client;

    @Mock
    private PokemonSyncService syncService;

    private PokemonCatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new PokemonCatalogService(new PokeApiCache(client), syncService,
                new PokemonSyncThrottle(new NoOpCacheManager()));
    }

    @Test
    void shouldMapImageCategoryMassAndSkills() {
        when(client.findByNameOrId("bulbasaur")).thenReturn(new PokemonDetailsResponse(
                1, "bulbasaur", 7, 69, new PokemonDetailsResponse.Sprites("img-1"),
                List.of(new PokemonDetailsResponse.AbilitySlot(new PokemonDetailsResponse.Ability("overgrow", "u-1")),
                        new PokemonDetailsResponse.AbilitySlot(new PokemonDetailsResponse.Ability("chlorophyll", "u-2"))),
                null));
        when(client.findSpeciesByNameOrId("bulbasaur")).thenReturn(new PokemonSpeciesResponse(
                List.of(new PokemonSpeciesResponse.Genus("Pokemon Graine", new PokemonSpeciesResponse.Language("fr")),
                        new PokemonSpeciesResponse.Genus("Seed Pokemon", new PokemonSpeciesResponse.Language("en"))),
                List.of(), null));

        PokemonItem item = catalogService.fetchItem("bulbasaur");

        assertEquals(1, item.id());
        assertEquals("bulbasaur", item.name());
        assertEquals("img-1", item.image());
        assertEquals("Seed Pokemon", item.category());
        assertEquals(6.9, item.mass());
        assertEquals(List.of("overgrow", "chlorophyll"), item.skills().stream().map(s -> s.name()).toList());
        assertTrue(item.available());
        // the list entry stores the same complete data the detail view would
        verify(syncService).sync(new PokemonSnapshot(1, "bulbasaur", "img-1", 0.7, 6.9, "Seed Pokemon",
                item.skills(), List.of(), null, true));
    }

    @Test
    void shouldResolveCategoryOfAlternateFormThroughItsSpeciesName() {
        when(client.findByNameOrId("deoxys-attack")).thenReturn(new PokemonDetailsResponse(
                10001, "deoxys-attack", 17, 608, new PokemonDetailsResponse.Sprites("deoxys-attack.png"),
                List.of(), null, new PokemonReference("deoxys", "https://pokeapi.co/api/v2/pokemon-species/386/")));
        when(client.findSpeciesByNameOrId("deoxys")).thenReturn(new PokemonSpeciesResponse(
                List.of(new PokemonSpeciesResponse.Genus("DNA Pokemon", new PokemonSpeciesResponse.Language("en"))),
                List.of(), null));

        PokemonItem item = catalogService.fetchItem("deoxys-attack");

        assertTrue(item.available());
        assertEquals(10001, item.id());
        assertEquals("deoxys-attack", item.name());
        assertEquals("DNA Pokemon", item.category());
        verify(client, never()).findSpeciesByNameOrId("deoxys-attack");
    }

    @Test
    void shouldStoreHeightStatsAndDescriptionFromTheListEntry() {
        when(client.findByNameOrId("pikachu")).thenReturn(new PokemonDetailsResponse(
                25, "pikachu", 4, 60, new PokemonDetailsResponse.Sprites("pikachu.png"), List.of(),
                List.of(new PokemonDetailsResponse.StatSlot(35, new PokemonDetailsResponse.Stat("hp", "u")))));
        when(client.findSpeciesByNameOrId("pikachu")).thenReturn(new PokemonSpeciesResponse(
                List.of(new PokemonSpeciesResponse.Genus("Mouse Pokemon", new PokemonSpeciesResponse.Language("en"))),
                List.of(new PokemonSpeciesResponse.FlavorTextEntry("Electric\nmouse.",
                        new PokemonSpeciesResponse.Language("en"))),
                null));

        catalogService.fetchItem("pikachu");

        var captor = ArgumentCaptor.forClass(PokemonSnapshot.class);
        verify(syncService).sync(captor.capture());
        assertEquals(0.4, captor.getValue().height());
        assertEquals(35, captor.getValue().stats().getFirst().value());
        assertEquals("Electric mouse.", captor.getValue().description());
        assertTrue(captor.getValue().speciesAvailable());
    }

    @Test
    void shouldReturnNullCategoryAndEmptySkillsWhenDataMissing() {
        when(client.findByNameOrId("ditto")).thenReturn(new PokemonDetailsResponse(
                132, "ditto", 3, 40, null, null, null));
        when(client.findSpeciesByNameOrId("ditto")).thenReturn(new PokemonSpeciesResponse(null, null, null));

        PokemonItem item = catalogService.fetchItem("ditto");

        assertNull(item.category());
        assertNull(item.image());
        assertTrue(item.skills().isEmpty());
    }

    @Test
    void shouldReturnItemEvenWhenLocalSyncFails() {
        when(client.findByNameOrId("pikachu")).thenReturn(new PokemonDetailsResponse(
                25, "pikachu", 4, 60, null, List.of(), List.of()));
        when(client.findSpeciesByNameOrId("pikachu")).thenReturn(new PokemonSpeciesResponse(List.of(), List.of(), null));
        doThrow(new RuntimeException("database down")).when(syncService).sync(any());

        PokemonItem item = catalogService.fetchItem("pikachu");

        assertEquals(25, item.id());
        assertEquals(6.0, item.mass());
    }

    @Test
    void shouldKeepImageMassAndSkillsWhenOnlyTheSpeciesFails() {
        when(client.findByNameOrId("bulbasaur")).thenReturn(new PokemonDetailsResponse(
                1, "bulbasaur", 7, 69, new PokemonDetailsResponse.Sprites("img-1"),
                List.of(new PokemonDetailsResponse.AbilitySlot(new PokemonDetailsResponse.Ability("overgrow", "u-1"))),
                null));
        when(client.findSpeciesByNameOrId("bulbasaur")).thenThrow(new RuntimeException("upstream 500"));

        PokemonItem item = catalogService.fetchItem("bulbasaur");

        assertTrue(item.available());
        assertFalse(item.categoryAvailable());
        assertTrue(item.partial());
        assertEquals("img-1", item.image());
        assertEquals(6.9, item.mass());
        assertEquals(List.of("overgrow"), item.skills().stream().map(s -> s.name()).toList());
        assertNull(item.category());
        // A partial copy is not stored: the next complete read stores it, and the throttle is not spent here.
        verifyNoInteractions(syncService);
    }

    @Test
    void shouldTellTheCallerWhenTheSpeciesWasRateLimited() {
        when(client.findByNameOrId("bulbasaur")).thenReturn(new PokemonDetailsResponse(
                1, "bulbasaur", 7, 69, new PokemonDetailsResponse.Sprites("img-1"), List.of(), null));
        var rateLimited = new PokeApiRateLimitException(Instant.now().plusSeconds(10), true, null);
        when(client.findSpeciesByNameOrId("bulbasaur")).thenThrow(rateLimited);
        var reported = new ArrayList<PokeApiRateLimitException>();

        PokemonItem item = catalogService.fetchItem("bulbasaur", reported::add);

        assertFalse(item.categoryAvailable());
        assertEquals("img-1", item.image());
        assertEquals(List.of(rateLimited), reported);
    }

    @Test
    void shouldPropagateUpstreamFailureForItem() {
        when(client.findByNameOrId("pikachu")).thenThrow(new RuntimeException("upstream 500"));

        assertThrows(RuntimeException.class, () -> catalogService.fetchItem("pikachu"));
    }

    @Test
    void shouldFetchReferencesUsingOffsetAndLimit() {
        var list = new PokemonListResponse(1302, List.of(new PokemonReference("bulbasaur", "u1")));
        when(client.findAll(40, 20)).thenReturn(list);

        assertEquals(list, catalogService.fetchReferences(40, 20));
    }

    @Test
    void shouldFailWhenUpstreamReturnsEmptyListBody() {
        when(client.findAll(0, 20)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> catalogService.fetchReferences(0, 20));
    }
}
