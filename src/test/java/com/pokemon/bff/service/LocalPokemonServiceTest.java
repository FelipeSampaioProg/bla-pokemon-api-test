package com.pokemon.bff.service;

import com.pokemon.bff.client.PokemonClient;
import com.pokemon.bff.client.dto.*;
import com.pokemon.bff.dto.*;
import com.pokemon.bff.persistence.entity.PokemonEntity;
import com.pokemon.bff.persistence.entity.PokemonMetadataEntity;
import com.pokemon.bff.persistence.entity.PokemonSkillEntity;
import com.pokemon.bff.persistence.entity.PokemonStatEntity;
import com.pokemon.bff.persistence.entity.PokemonTombstoneEntity;
import com.pokemon.bff.persistence.repository.PokemonRepository;
import com.pokemon.bff.persistence.repository.PokemonTombstoneRepository;
import com.pokemon.bff.client.PokeApiNotFoundException;
import com.pokemon.bff.client.PokeApiRateLimitException;
import com.pokemon.bff.sync.PokemonSnapshot;
import com.pokemon.bff.sync.PokemonSyncService;
import com.pokemon.bff.sync.PokemonSyncThrottle;
import org.springframework.cache.support.NoOpCacheManager;
import net.datafaker.Faker;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests of the local records: reads, creation, replacement, partial update, deletion and replication. */
@ExtendWith(MockitoExtension.class)
class LocalPokemonServiceTest {
    private static final String POKEMON_BULBASAUR = "bulbasaur";

    private static final String POKEMON_IVYSAUR = "ivysaur";

    private static final String POKEMON_VENUSAUR = "venusaur";

    @Mock
    private PokemonClient client;

    @Mock
    private PokemonSyncService syncService;

    @Mock
    private PokemonRepository pokemonRepository;

    @Mock
    private PokemonTombstoneRepository tombstoneRepository;

    @Mock
    private PokemonCatalogService catalogService;

    private PokeApiPokemonService service;

    private LocalPokemonService localService;

    @BeforeEach
    void setUp() {
        // Runs the per-item tasks on the calling thread so the tests stay deterministic.
        // Real PokeApiCache over the mocked client (no Spring proxy, so no caching here) and a throttle that always
        // lets the sync through.
        var pokeApi = new PokeApiCache(client);
        service = new PokeApiPokemonService(pokeApi, syncService, new PokemonSyncThrottle(new NoOpCacheManager()),
                pokemonRepository, catalogService, Runnable::run);
        localService = new LocalPokemonService(service, pokeApi, syncService, pokemonRepository, tombstoneRepository);
    }

    @Test
    void shouldListLocalPokemonFromRepository() {
        var local = new PokemonEntity(9001, "localmon", "local.png", 1.0, 12.5, "Created locally", Instant.now());
        local.getSkills().add(new PokemonSkillEntity(local, "splash", null));
        local.setCategory("Custom Pokemon");
        var metadata = new PokemonMetadataEntity();
        metadata.setClassificationTag("CUSTOM");
        local.setMetadata(metadata);
        var synced = new PokemonEntity(1, "bulbasaur", "img-1", 0.7, 6.9, null, Instant.now());
        synced.setCategory("Seed Pokemon");
        when(pokemonRepository.findAll(any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(synced, local), inv.getArgument(0), 3));

        var page = localService.findLocalPage(0, 2);

        assertEquals(2, page.content().size());
        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals("Seed Pokemon", page.content().getFirst().category());
        var localItem = page.content().get(1);
        assertEquals(9001, localItem.id());
        assertEquals("local.png", localItem.image());
        // category comes from the category column, never from the classification tag
        assertEquals("Custom Pokemon", localItem.category());
        assertEquals(12.5, localItem.mass());
        assertEquals("splash", localItem.skills().getFirst().name());
        verifyNoInteractions(catalogService);
    }

    @Test
    void shouldNotUseClassificationTagAsCategory() {
        var local = new PokemonEntity(9001, "localmon", null, null, null, null, Instant.now());
        var metadata = new PokemonMetadataEntity();
        metadata.setClassificationTag("STARTER");
        local.setMetadata(metadata);
        when(pokemonRepository.findAll(any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(local), inv.getArgument(0), 1));

        assertNull(localService.findLocalPage(0, 20).content().getFirst().category());
    }

    @Test
    void shouldFindLocalDetailById() {
        var local = new PokemonEntity(9001, "localmon", "local.png", null, 12.5, "Created locally", Instant.now());
        local.getStats().add(new PokemonStatEntity(local, "hp", 50));
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(local));

        var detail = localService.findLocalByNameOrId(" 9001 ");

        assertEquals(9001, detail.id());
        assertEquals("local.png", detail.image());
        assertNull(detail.height());
        assertEquals(12.5, detail.mass());
        assertEquals(List.of(), detail.skills());
        assertEquals("hp", detail.stats().getFirst().name());
        assertEquals(50, detail.stats().getFirst().value());
        assertEquals("Created locally", detail.description());
        assertEquals("localmon", detail.evolutionLineage().name());
        assertTrue(detail.evolutionLineage().evolvesTo().isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void shouldFindLocalDetailByNormalizedName() {
        var local = new PokemonEntity(9001, "localmon", null, 1.0, 12.5, null, Instant.now());
        when(pokemonRepository.findByName("localmon")).thenReturn(Optional.of(local));

        assertEquals(9001, localService.findLocalByNameOrId("LocalMon").id());
    }

    @Test
    void shouldRejectMissingBlankOrOutOfRangeLocalDetail() {
        when(pokemonRepository.findByName("pikachu")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> localService.findLocalByNameOrId("pikachu"));
        assertThrows(IllegalArgumentException.class, () -> localService.findLocalByNameOrId("  "));
        assertThrows(IllegalArgumentException.class, () -> localService.findLocalByNameOrId("99999999999"));
    }

    @Test
    void shouldStoreTrimmedCategoryOnCreate() {
        var request = new PokemonCreateRequest(9001, "localmon", null, 1.0, 12.5, null, "  Custom Pokemon ",
                List.of(), List.of(), null, null, null);
        when(pokemonRepository.existsById(9001)).thenReturn(false);
        when(pokemonRepository.findByName("localmon")).thenReturn(Optional.empty());
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("Custom Pokemon", localService.createPokemon(request).category());
    }

    @Test
    void shouldUpdateAndClearCategory() {
        var existing = new PokemonEntity(9001, "localmon", null, 1.0, 12.5, null, Instant.now());
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(existing));
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var updated = localService.updatePokemon(9001, new PokemonUpdateRequest(null, null, null, null, null,
                "Custom Pokemon", null, null, null, null, null));
        assertEquals("Custom Pokemon", updated.category());

        var cleared = localService.updatePokemon(9001, new PokemonUpdateRequest(null, null, null, null, null,
                " ", null, null, null, null, null));
        assertNull(cleared.category());
    }

    @Test
    void shouldReturnStatsSkillsAndMetadataInResponse() {
        var request = new PokemonCreateRequest(9001, "localmon", "https://img.example/local.png", 1.0, 12.5, "Created locally",
                "Custom Pokemon", List.of(new PokemonStat("hp", 50)), List.of(new Skill("splash", "https://pokeapi.co/api/v2/ability/150/")),
                "Localmon", "Kanto", "CUSTOM");
        when(pokemonRepository.existsById(9001)).thenReturn(false);
        when(pokemonRepository.findByName("localmon")).thenReturn(Optional.empty());
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = localService.createPokemon(request);

        // The same representation as GET /local-pokemons/{id} (User Story 04: consistent return structures).
        assertEquals(new PokemonDetail(9001, "localmon", "localmon", "https://img.example/local.png", 1.0, 12.5,
                "Custom Pokemon", List.of(new Skill("splash", "https://pokeapi.co/api/v2/ability/150/")),
                List.of(new PokemonStat("hp", 50)), "Created locally", true, new EvolutionNode("localmon", List.of()),
                true, null, new PokemonMetadata("Localmon", "Kanto", "CUSTOM")), response);
    }

    @Test
    void shouldRejectCategoryLongerThanColumn() {
        var request = new PokemonCreateRequest(9001, "localmon", null, 1.0, 12.5, null, "x".repeat(101),
                List.of(), List.of(), null, null, null);

        assertThrows(IllegalArgumentException.class, () -> localService.createPokemon(request));
    }

    @Test
    void shouldRejectInvalidPaginationForLocalPage() {
        assertThrows(IllegalArgumentException.class, () -> localService.findLocalPage(0, 101));
        verify(pokemonRepository, never()).findAll(any(Pageable.class));
    }

    // --- createPokemon ---

    @Test
    void shouldCreatePokemonSuccessfully() {
        var request = new PokemonCreateRequest(25, "pikachu", "https://img.example/pikachu.png", 0.4, 6.0,
                "Electric mouse.", List.of(new PokemonStat("hp", 35)), List.of(), null, null, null);
        PokemonEntity saved = new PokemonEntity(25, "pikachu", "img.png", 0.4, 6.0, "Electric mouse.", Instant.now());
        when(pokemonRepository.existsById(25)).thenReturn(false);
        when(pokemonRepository.findByName("pikachu")).thenReturn(Optional.empty());
        when(pokemonRepository.save(any())).thenReturn(saved);

        var result = localService.createPokemon(request);

        assertEquals(25, result.id());
        assertEquals("pikachu", result.name());
        verify(pokemonRepository).save(any());
    }

    @Test
    void shouldThrowWhenCreateIdAlreadyExists() {
        var request = new PokemonCreateRequest(25, "pikachu", null, null, null, null, List.of(), List.of(), null, null, null);
        when(pokemonRepository.existsById(25)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> localService.createPokemon(request));
    }

    @Test
    void shouldThrowWhenCreateNameAlreadyExists() {
        var request = new PokemonCreateRequest(999, "pikachu", null, null, null, null, List.of(), List.of(), null, null, null);
        PokemonEntity existing = new PokemonEntity(25, "pikachu", null, null, null, null, Instant.now());
        when(pokemonRepository.existsById(999)).thenReturn(false);
        when(pokemonRepository.findByName("pikachu")).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> localService.createPokemon(request));
    }

    @Test
    void shouldThrowWhenCreateRequestHasNullId() {
        var request = new PokemonCreateRequest(null, "pikachu", null, null, null, null, List.of(), List.of(), null, null, null);

        assertThrows(IllegalArgumentException.class, () -> localService.createPokemon(request));
    }

    @Test
    void shouldThrowWhenCreateRequestHasBlankName() {
        var request = new PokemonCreateRequest(1, "  ", null, null, null, null, List.of(), List.of(), null, null, null);

        assertThrows(IllegalArgumentException.class, () -> localService.createPokemon(request));
    }

    @Test
    void shouldCreatePokemonWithMetadataWhenProvided() {
        var request = new PokemonCreateRequest(1, "bulbasaur", null, 0.7, 6.9, null,
                List.of(), List.of(), "Fushigidane", "Kanto", "STARTER");
        when(pokemonRepository.existsById(1)).thenReturn(false);
        when(pokemonRepository.findByName("bulbasaur")).thenReturn(Optional.empty());
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = localService.createPokemon(request);

        assertEquals("Fushigidane", result.metadata().localizedName());
        assertEquals("Kanto", result.metadata().region());
        assertEquals("STARTER", result.metadata().classificationTag());
        var captor = ArgumentCaptor.forClass(PokemonEntity.class);
        verify(pokemonRepository).save(captor.capture());
        assertTrue(captor.getValue().isLocallyModified());
    }

    // --- updatePokemon ---

    @Test
    void shouldUpdatePokemonNameSuccessfully() {
        PokemonEntity existing = new PokemonEntity(1, "bulbasaur", "img.png", 0.7, 6.9, "desc", Instant.now());
        when(pokemonRepository.findById(1)).thenReturn(Optional.of(existing));
        when(pokemonRepository.findByName("bulbasaur-v2")).thenReturn(Optional.empty());
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new PokemonUpdateRequest("bulbasaur-v2", null, null, null, null,
                List.of(), List.of(), null, null, null);
        var result = localService.updatePokemon(1, request);

        assertEquals("bulbasaur-v2", result.name());
        assertTrue(existing.isLocallyModified());
    }

    @Test
    void shouldThrowWhenUpdatePokemonNotFound() {
        when(pokemonRepository.findById(99)).thenReturn(Optional.empty());

        var request = new PokemonUpdateRequest("new-name", null, null, null, null,
                List.of(), List.of(), null, null, null);

        assertThrows(NoSuchElementException.class, () -> localService.updatePokemon(99, request));
    }

    @Test
    void shouldThrowWhenUpdateIdIsInvalid() {
        var request = new PokemonUpdateRequest("name", null, null, null, null,
                List.of(), List.of(), null, null, null);

        assertThrows(IllegalArgumentException.class, () -> localService.updatePokemon(0, request));
    }

    @Test
    void shouldThrowWhenUpdateRequestHasNoFields() {
        var request = new PokemonUpdateRequest(null, null, null, null, null,
                null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> localService.updatePokemon(1, request));
    }

    @Test
    void shouldUpdateMetadataWhenProvided() {
        PokemonEntity existing = new PokemonEntity(1, "bulbasaur", null, 0.7, 6.9, null, Instant.now());
        when(pokemonRepository.findById(1)).thenReturn(Optional.of(existing));
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new PokemonUpdateRequest(null, null, null, null, null,
                List.of(), List.of(), "Fushigidane", "Kanto", null);
        var result = localService.updatePokemon(1, request);

        assertEquals("Fushigidane", result.metadata().localizedName());
        assertEquals("Kanto", result.metadata().region());
    }

    // --- replacePokemon (PUT) ---

    @Test
    void shouldReplaceEveryFieldAndClearWhatIsLeftOut() {
        var existing = new PokemonEntity(9001, "localmon", "old.png", 1.0, 12.5, "old description", Instant.now());
        existing.setCategory("Old category");
        existing.getStats().add(new PokemonStatEntity(existing, "hp", 50));
        existing.getSkills().add(new PokemonSkillEntity(existing, "splash", null));
        var metadata = new PokemonMetadataEntity();
        metadata.setPokemon(existing);
        metadata.setRegion("Kanto");
        existing.setMetadata(metadata);
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(existing));
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = localService.replacePokemon(9001, new PokemonReplaceRequest("Localmon", null, null, 2.0, null,
                null, List.of(new PokemonStat("speed", 99)), null, null, null, null));

        assertEquals("localmon", result.name());
        assertEquals(2.0, result.mass());
        assertNull(result.image());
        assertNull(result.height());
        assertNull(result.description());
        assertNull(result.category());
        assertEquals(List.of(new PokemonStat("speed", 99)), result.stats());
        assertTrue(result.skills().isEmpty());
        assertNull(result.metadata() == null ? null : result.metadata().region());
        assertNull(existing.getMetadata());
        assertTrue(existing.isLocallyModified());
    }

    @Test
    void shouldReplaceMetadataAsAWhole() {
        var existing = new PokemonEntity(9001, "localmon", null, null, null, null, Instant.now());
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(existing));
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = localService.replacePokemon(9001, new PokemonReplaceRequest("localmon", null, null, null, null,
                null, null, null, "Localmon", "Johto", null));

        assertEquals("Localmon", result.metadata().localizedName());
        assertEquals("Johto", result.metadata().region());
        assertNull(result.metadata().classificationTag());
    }

    @Test
    void shouldRejectInvalidReplacements() {
        assertThrows(IllegalArgumentException.class, () -> localService.replacePokemon(9001, null));
        assertThrows(IllegalArgumentException.class, () -> localService.replacePokemon(9001,
                new PokemonReplaceRequest(" ", null, null, null, null, null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> localService.replacePokemon(0,
                new PokemonReplaceRequest("x", null, null, null, null, null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> localService.replacePokemon(9001,
                new PokemonReplaceRequest("x", null, null, -1.0, null, null, null, null, null, null, null)));
        verify(pokemonRepository, never()).findById(anyInt());
    }

    @Test
    void shouldReturnNotFoundWhenReplacingAMissingPokemon() {
        when(pokemonRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> localService.replacePokemon(404,
                new PokemonReplaceRequest("ghost", null, null, null, null, null, null, null, null, null, null)));
    }

    @Test
    void shouldRejectReplacingWithANameAlreadyInUse() {
        var existing = new PokemonEntity(9001, "localmon", null, null, null, null, Instant.now());
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(existing));
        when(pokemonRepository.findByName("pikachu"))
                .thenReturn(Optional.of(new PokemonEntity(25, "pikachu", null, null, null, null, Instant.now())));

        assertThrows(IllegalArgumentException.class, () -> localService.replacePokemon(9001,
                new PokemonReplaceRequest("Pikachu", null, null, null, null, null, null, null, null, null, null)));
    }

    @Test
    void shouldKeepReplicatingWhenOnlyProprietaryFieldsChange() {
        var entity = replicatedPikachu();
        when(pokemonRepository.existsById(25)).thenReturn(true);
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(entity));
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = localService.updatePokemon(25, new PokemonUpdateRequest(null, null, null, null, null, null,
                null, null, "Pikachu (ES)", "Kanto", "STARTER"));

        assertEquals("Kanto", result.metadata().region());
        assertFalse(entity.isLocallyModified(), "proprietary fields must not take the record over");
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), entity.getSyncedAt(), "it was not synchronised");
        verify(syncService, never()).syncNow(any());
    }

    @Test
    void shouldClearAProprietaryFieldSentBlankAndTrimTheOthers() {
        var entity = replicatedPikachu();
        var metadata = new PokemonMetadataEntity();
        metadata.setPokemon(entity);
        metadata.setRegion("Kanto");
        metadata.setLocalizedName("Pikachu (ES)");
        entity.setMetadata(metadata);
        when(pokemonRepository.existsById(25)).thenReturn(true);
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(entity));
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = localService.updatePokemon(25, new PokemonUpdateRequest(null, null, null, null, null, null,
                null, null, " ", "  Johto ", null));

        assertNull(result.metadata().localizedName());
        assertEquals("Johto", result.metadata().region());
        assertFalse(entity.isLocallyModified());
    }

    @Test
    void shouldRejectProprietaryFieldsLongerThanTheirColumns() {
        assertThrows(IllegalArgumentException.class, () -> localService.updatePokemon(25, new PokemonUpdateRequest(
                null, null, null, null, null, null, null, null, "x".repeat(201), null, null)));
        assertThrows(IllegalArgumentException.class, () -> localService.updatePokemon(25, new PokemonUpdateRequest(
                null, null, null, null, null, null, null, null, null, "x".repeat(101), null)));
        assertThrows(IllegalArgumentException.class, () -> localService.createPokemon(new PokemonCreateRequest(
                9001, "localmon", null, 1.0, 1.0, null, List.of(), List.of(), null, null, "x".repeat(101))));
        verifyNoInteractions(pokemonRepository);
    }

    @Test
    void shouldTakeTheRecordOverWhenPokeApiFieldsChange() {
        var entity = replicatedPikachu();
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(entity));
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        localService.updatePokemon(25, new PokemonUpdateRequest(null, null, null, 7.0, null, null,
                null, null, null, "Kanto", null));

        assertTrue(entity.isLocallyModified());
    }

    @Test
    void shouldReplicateFirstWhenEnrichingAPokemonNotInTheLocalStore() {
        var replicated = replicatedPikachu();
        when(pokemonRepository.existsById(25)).thenReturn(false);
        stubPikachuInPokeApi("25");
        when(syncService.syncNow(any())).thenReturn(new Versioned<>(new PokemonSyncResult(
                PokemonSyncResult.Outcome.CREATED, PokemonDetail.local(replicated)), "0-1"));
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(replicated));
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = localService.updatePokemon(25, new PokemonUpdateRequest(null, null, null, null, null, null,
                null, null, null, "Kanto", null));

        var snapshot = ArgumentCaptor.forClass(PokemonSnapshot.class);
        verify(syncService).syncNow(snapshot.capture());
        assertEquals(25, snapshot.getValue().id());
        assertEquals("Kanto", result.metadata().region());
        assertFalse(replicated.isLocallyModified());
    }

    @Test
    void shouldStillAnswerNotFoundWhenEnrichingAPokemonPokeApiDoesNotKnow() {
        when(pokemonRepository.existsById(99999)).thenReturn(false);
        when(client.findByNameOrId("99999")).thenThrow(notFound("/pokemon/99999"));
        when(client.findSpeciesByNameOrId("99999")).thenThrow(notFound("/pokemon-species/99999"));

        var error = assertThrows(NoSuchElementException.class, () -> localService.updatePokemon(99999,
                new PokemonUpdateRequest(null, null, null, null, null, null, null, null, null, "Kanto", null)));
        assertEquals("Pokemon not found with id 99999 in the local store nor in PokeAPI", error.getMessage());
        verify(syncService, never()).syncNow(any());
    }

    @Test
    void shouldNotReplicateFirstWhenTheUpdateChangesPokeApiFields() {
        when(pokemonRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> localService.updatePokemon(404,
                new PokemonUpdateRequest(null, null, null, 7.0, null, null, null, null, null, null, null)));
        verifyNoInteractions(client);
    }

    @Test
    void shouldReplicateOnDemand() {
        stubPikachuInPokeApi("pikachu");
        var expected = new Versioned<>(new PokemonSyncResult(PokemonSyncResult.Outcome.CREATED,
                PokemonDetail.local(replicatedPikachu())), "0-1");
        when(syncService.syncNow(any())).thenReturn(expected);

        var result = localService.synchronize(" Pikachu ");

        assertEquals(expected, result);
        var snapshot = ArgumentCaptor.forClass(PokemonSnapshot.class);
        verify(syncService).syncNow(snapshot.capture());
        assertEquals("pikachu.png", snapshot.getValue().image());
        assertThrows(IllegalArgumentException.class, () -> localService.synchronize(" "));
    }

    @Test
    void shouldRememberADeletionSoTheSyncDoesNotRecreateTheRecord() {
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(replicatedPikachu()));

        localService.deletePokemon(25);

        var tombstone = ArgumentCaptor.forClass(PokemonTombstoneEntity.class);
        verify(tombstoneRepository).save(tombstone.capture());
        assertEquals(25, tombstone.getValue().getPokemonId());
    }

    @Test
    void shouldLiftADeletionMarkWhenTheRecordIsCreatedAgain() {
        when(pokemonRepository.existsById(9001)).thenReturn(false);
        when(pokemonRepository.findByName("localmon")).thenReturn(Optional.empty());
        when(client.findByNameOrId("localmon")).thenThrow(notFound("/pokemon/localmon"));
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        localService.createPokemon(new PokemonCreateRequest(9001, "localmon", null, 1.0, 1.0, null,
                List.of(), List.of(), null, null, null));

        verify(tombstoneRepository).deleteById(9001);
    }

    @Test
    void shouldRefuseALocalNameThatBelongsToAnotherPokeApiPokemon() {
        when(pokemonRepository.existsById(9001)).thenReturn(false);
        when(pokemonRepository.findByName("pikachu")).thenReturn(Optional.empty());
        stubPikachuPokemon("pikachu");

        var error = assertThrows(IllegalArgumentException.class, () -> localService.createPokemon(new PokemonCreateRequest(
                9001, "Pikachu", null, 1.0, 1.0, null, List.of(), List.of(), null, null, null)));

        assertEquals("Pokemon name pikachu belongs to PokeAPI Pokemon #25; choose another name", error.getMessage());
        verify(pokemonRepository, never()).save(any());
    }

    @Test
    void shouldAllowTheNameOfThePokeApiPokemonWithTheSameId() {
        when(pokemonRepository.existsById(25)).thenReturn(false);
        when(pokemonRepository.findByName("pikachu")).thenReturn(Optional.empty());
        stubPikachuPokemon("pikachu");
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(25, localService.createPokemon(new PokemonCreateRequest(25, "pikachu", null, 0.4, 6.0, null,
                List.of(), List.of(), null, null, null)).id());
    }

    @Test
    void shouldRefuseRenamingToAPokeApiName() {
        var local = new PokemonEntity(9001, "localmon", null, null, null, null, Instant.now());
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(local));
        when(pokemonRepository.findByName("pikachu")).thenReturn(Optional.empty());
        stubPikachuPokemon("pikachu");

        assertThrows(IllegalArgumentException.class, () -> localService.updatePokemon(9001,
                new PokemonUpdateRequest("pikachu", null, null, null, null, null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> localService.replacePokemon(9001,
                new PokemonReplaceRequest("pikachu", null, null, null, null, null, null, null, null, null, null)));
    }

    @Test
    void shouldShowProprietaryFieldsInLocalReads() {
        var stored = replicatedPikachu();
        var metadata = new PokemonMetadataEntity();
        metadata.setPokemon(stored);
        metadata.setClassificationTag("STARTER");
        stored.setMetadata(metadata);
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(stored));
        when(pokemonRepository.findAll(any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(stored), inv.getArgument(0), 1));

        assertEquals("STARTER", localService.findLocalByNameOrId("25").metadata().classificationTag());
        assertEquals("STARTER", localService.findLocalPage(0, 20).content().getFirst().metadata().classificationTag());
    }

    // --- deletePokemon ---

    @Test
    void shouldDeletePokemonSuccessfully() {
        PokemonEntity existing = new PokemonEntity(1, "bulbasaur", null, 0.7, 6.9, null, Instant.now());
        when(pokemonRepository.findById(1)).thenReturn(Optional.of(existing));

        localService.deletePokemon(1);

        verify(pokemonRepository).delete(existing);
    }

    @Test
    void shouldThrowWhenDeletePokemonNotFound() {
        when(pokemonRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> localService.deletePokemon(99));
    }

    @Test
    void shouldThrowWhenDeleteIdIsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> localService.deletePokemon(-1));
        assertThrows(IllegalArgumentException.class, () -> localService.deletePokemon(null));
    }

    @Test
    void shouldUpdateExistingPokemonWithLocalMetadata() {
        var entity = new PokemonEntity(25, "pikachu", "old.png", 0.4, 6.0, "old description", Instant.now());
        entity.setMetadata(new PokemonMetadataEntity());
        entity.getMetadata().setPokemon(entity);
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(entity));
        when(pokemonRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = localService.updatePokemon(25, new PokemonUpdateRequest(
                "pikachu",
                "updated description",
                "https://img.example/new.png",
                "Pikachu",
                "Kanto",
                "ELECTRIC"));

        assertEquals("updated description", updated.description());
        assertEquals("https://img.example/new.png", updated.image());
        assertEquals("Pikachu", entity.getMetadata().getLocalizedName());
        assertEquals("Kanto", entity.getMetadata().getRegion());
        assertEquals("ELECTRIC", entity.getMetadata().getClassificationTag());
        verify(pokemonRepository).save(entity);
    }

    @Test
    void shouldRejectMissingPokemonAndEmptyPayload() {
        when(pokemonRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> localService.updatePokemon(10, new PokemonUpdateRequest("   ", "   ", null, null, null, null)));
        assertThrows(java.util.NoSuchElementException.class,
                () -> localService.updatePokemon(999, new PokemonUpdateRequest("pikachu", "updated", null, null, null, null)));
    }

    @Test
    void shouldCreateLocalPokemon() {
        when(pokemonRepository.existsById(2001)).thenReturn(false);
        when(pokemonRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = localService.createPokemon(new PokemonCreateRequest(
                2001,
                "TestMon",
                "https://img.example/sprite.png",
                1.2,
                15.5,
                "Local pokemon",
                List.of(new PokemonStat("speed", 99)),
                "Test Mon",
                "Johto",
                "CUSTOM"));

        assertEquals(2001, created.id());
        assertEquals("testmon", created.name());
        assertEquals("https://img.example/sprite.png", created.image());
        assertEquals(List.of(new PokemonStat("speed", 99)), created.stats());
        verify(pokemonRepository).save(any(PokemonEntity.class));
    }

    @Test
    void shouldRejectInvalidCreateAndDeleteMissingPokemon() {
        when(pokemonRepository.existsById(25)).thenReturn(true);
        when(pokemonRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> localService.createPokemon(new PokemonCreateRequest(
                        25,
                        "pikachu",
                        null,
                        0.4,
                        6.0,
                        (String) null,
                        List.of(),
                        null,
                        null,
                        null)));
        assertThrows(IllegalArgumentException.class,
                () -> localService.createPokemon(new PokemonCreateRequest(
                        2002,
                        " ",
                        null,
                        0.4,
                        6.0,
                        (String) null,
                        List.of(),
                        null,
                        null,
                        null)));
        assertThrows(java.util.NoSuchElementException.class, () -> localService.deletePokemon(404));
    }

    // --- User Story 03: replication and proprietary fields ---

    private PokemonEntity replicatedPikachu() {
        var entity = new PokemonEntity(25, "pikachu", "pikachu.png", 0.4, 6.0, "Electric mouse.", Instant.parse("2026-10-01T00:00:00Z"));
        entity.setCategory("Mouse Pokemon");
        return entity;
    }

    /** Only /pokemon: what the name check asks. */
    private void stubPikachuPokemon(String identifier) {
        when(client.findByNameOrId(identifier)).thenReturn(new PokemonDetailsResponse(
                25, "pikachu", 4, 60, new PokemonDetailsResponse.Sprites("pikachu.png"), List.of(), List.of()));
    }

    private void stubPikachuInPokeApi(String identifier) {
        when(client.findByNameOrId(identifier)).thenReturn(new PokemonDetailsResponse(
                25, "pikachu", 4, 60, new PokemonDetailsResponse.Sprites("pikachu.png"), List.of(), List.of()));
        when(client.findSpeciesByNameOrId("pikachu")).thenReturn(new PokemonSpeciesResponse(List.of(), List.of(), null));
    }

    private static FeignException notFound(String path) {
        var request = Request.create(Request.HttpMethod.GET, "https://pokeapi.co/api/v2" + path, Map.of(),
                null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("PokemonClient#find",
                Response.builder().status(404).reason("Not Found").request(request).headers(Map.of()).build());
    }
}
