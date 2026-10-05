package com.pokemon.bff.service;

import com.pokemon.bff.client.PokemonClient;
import com.pokemon.bff.dto.PokemonCreateRequest;
import com.pokemon.bff.dto.PokemonReplaceRequest;
import com.pokemon.bff.dto.PokemonStat;
import com.pokemon.bff.dto.PokemonUpdateRequest;
import com.pokemon.bff.dto.Skill;
import com.pokemon.bff.exception.InvalidFieldException;
import com.pokemon.bff.exception.PreconditionFailedException;
import com.pokemon.bff.persistence.entity.PokemonEntity;
import com.pokemon.bff.persistence.repository.PokemonRepository;
import com.pokemon.bff.persistence.repository.PokemonTombstoneRepository;
import com.pokemon.bff.sync.PokemonSyncService;
import com.pokemon.bff.sync.PokemonSyncThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.support.NoOpCacheManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * User Story 04 — local data modification: field validation of the writes, the {@code If-Match} precondition,
 * the version bump behind the ETag, and the 404 of an update on a deleted record.
 */
@ExtendWith(MockitoExtension.class)
class LocalPokemonServiceWriteRulesTest {
    private static final Instant CREATED = Instant.parse("2026-10-01T00:00:00Z");

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

    private LocalPokemonService service;

    @BeforeEach
    void setUp() {
        var pokeApi = new PokeApiCache(client);
        var pokeApiPokemon = new PokeApiPokemonService(pokeApi, syncService,
                new PokemonSyncThrottle(new NoOpCacheManager()), pokemonRepository, catalogService, Runnable::run);
        service = new LocalPokemonService(pokeApiPokemon, pokeApi, syncService, pokemonRepository, tombstoneRepository);
    }

    private static PokemonEntity localmon(long version) {
        var entity = new PokemonEntity(9001, "localmon", "https://img.example/localmon.png", 1.0, 12.5,
                "Created locally", CREATED);
        entity.setVersion(version);
        entity.setLocallyModified(true);
        return entity;
    }

    /** The record's version token at this version number. */
    private static String version(long version) {
        return version + "-" + CREATED.toEpochMilli();
    }

    /** The condition of a client that read this version. */
    private static VersionCondition at(long version) {
        return VersionCondition.oneOf(List.of(version(version)));
    }

    private static PokemonUpdateRequest patch(String field, Object value) {
        return switch (field) {
            case "name" -> new PokemonUpdateRequest((String) value, null, null, null, null, null, null, null, null, null, null);
            case "image" -> new PokemonUpdateRequest(null, (String) value, null, null, null, null, null, null, null, null, null);
            case "height" -> new PokemonUpdateRequest(null, null, (Double) value, null, null, null, null, null, null, null, null);
            case "mass" -> new PokemonUpdateRequest(null, null, null, (Double) value, null, null, null, null, null, null, null);
            case "description" -> new PokemonUpdateRequest(null, null, null, null, (String) value, null, null, null, null, null, null);
            case "category" -> new PokemonUpdateRequest(null, null, null, null, null, (String) value, null, null, null, null, null);
            default -> throw new IllegalArgumentException(field);
        };
    }

    @SuppressWarnings("unchecked")
    private static PokemonUpdateRequest patchStats(List<?> stats) {
        return new PokemonUpdateRequest(null, null, null, null, null, null, (List<PokemonStat>) stats, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static PokemonUpdateRequest patchSkills(List<?> skills) {
        return new PokemonUpdateRequest(null, null, null, null, null, null, null, (List<Skill>) skills, null, null, null);
    }

    // --- field validation: 400 naming the field, before any database or PokeAPI call ---

    static Stream<Arguments> invalidUpdates() {
        var tooManyStats = IntStream.range(0, 21).mapToObj(i -> new PokemonStat("stat-" + i, 1)).toList();
        var tooManySkills = IntStream.range(0, 21).mapToObj(i -> new Skill("skill-" + i, null)).toList();
        return Stream.of(
                Arguments.of(patch("name", "x".repeat(101)), "name"),
                Arguments.of(patch("name", "mr mime"), "name"),
                Arguments.of(patch("name", "mr.mime"), "name"),
                Arguments.of(patch("name", "pika/chu"), "name"),
                Arguments.of(patch("name", "-pikachu"), "name"),
                Arguments.of(patch("name", "123"), "name"),
                Arguments.of(patch("name", "   "), "name"),
                Arguments.of(patch("image", "javascript:alert(1)"), "image"),
                Arguments.of(patch("image", "local.png"), "image"),
                Arguments.of(patch("image", "ftp://img.example/a.png"), "image"),
                Arguments.of(patch("image", "https://img.example/" + "a".repeat(500)), "image"),
                Arguments.of(patch("description", "d".repeat(5001)), "description"),
                Arguments.of(patch("category", "c".repeat(101)), "category"),
                Arguments.of(patch("height", -1.0), "height"),
                Arguments.of(patch("height", 1000.5), "height"),
                Arguments.of(patch("height", Double.NaN), "height"),
                Arguments.of(patch("mass", 10000.1), "mass"),
                Arguments.of(patch("mass", Double.POSITIVE_INFINITY), "mass"),
                Arguments.of(patchStats(tooManyStats), "stats"),
                Arguments.of(patchStats(Collections.singletonList(null)), "stats[0]"),
                Arguments.of(patchStats(List.of(new PokemonStat("hp", 10), new PokemonStat(" ", 10))), "stats[1].name"),
                Arguments.of(patchStats(List.of(new PokemonStat("s".repeat(101), 10))), "stats[0].name"),
                Arguments.of(patchStats(List.of(new PokemonStat("hp", 256))), "stats[0].value"),
                Arguments.of(patchStats(List.of(new PokemonStat("hp", -1))), "stats[0].value"),
                Arguments.of(patchStats(List.of(new PokemonStat("hp", 10), new PokemonStat("HP", 20))), "stats"),
                Arguments.of(patchSkills(tooManySkills), "skills"),
                Arguments.of(patchSkills(List.of(new Skill("static", "not a url"))), "skills[0].url"),
                Arguments.of(patchSkills(List.of(new Skill("static", null), new Skill(" Static ", null))), "skills"),
                Arguments.of(new PokemonUpdateRequest(9002, null, null, null, null, null, null, null, null, null,
                        "Kanto", null), "id"));
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void shouldRejectAnInvalidFieldNamingIt(PokemonUpdateRequest request, String field) {
        var error = assertThrows(InvalidFieldException.class, () -> service.updatePokemon(9001, request));

        assertEquals(field, error.getField());
        verifyNoInteractions(pokemonRepository, client);
    }

    @Test
    void shouldValidateCreateAndReplaceWithTheSameRules() {
        var create = assertThrows(InvalidFieldException.class, () -> service.createPokemon(new PokemonCreateRequest(
                9001, "mr mime", null, null, null, null, List.of(), List.of(), null, null, null)));
        assertEquals("name", create.getField());

        var replace = assertThrows(InvalidFieldException.class, () -> service.replacePokemon(9001,
                new PokemonReplaceRequest("localmon", null, null, null, null, null,
                        List.of(new PokemonStat("hp", 300)), null, null, null, null)));
        assertEquals("stats[0].value", replace.getField());

        var mismatchedId = assertThrows(InvalidFieldException.class, () -> service.replacePokemon(9001,
                new PokemonReplaceRequest(1, "localmon", null, null, null, null, null, null, null, null, null, null)));
        assertEquals("id", mismatchedId.getField());
        verifyNoInteractions(pokemonRepository, client);
    }

    @Test
    void shouldAcceptPokeApiStyleNamesAndValuesAtTheLimits() {
        var entity = localmon(1);
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(entity));
        when(pokemonRepository.findByName("porygon-z2")).thenReturn(Optional.empty());
        when(client.findByNameOrId("porygon-z2")).thenThrow(notFound());
        when(pokemonRepository.save(entity)).thenReturn(entity);

        var result = service.updatePokemon(9001, new PokemonUpdateRequest(9001, " Porygon-Z2 ", null, 1000.0,
                10000.0, null, null, List.of(new PokemonStat("hp", 255), new PokemonStat("speed", 0)),
                List.of(new Skill("download", "https://pokeapi.co/api/v2/ability/88/")), null, null, null));

        assertEquals("porygon-z2", result.name());
        assertEquals(1000.0, result.height());
        assertEquals(10000.0, result.mass());
        assertEquals(255, result.stats().get(0).value());
    }

    @Test
    void shouldClearImageAndDescriptionSentBlank() {
        var entity = localmon(1);
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(entity));
        when(pokemonRepository.save(entity)).thenReturn(entity);

        var result = service.updatePokemon(9001, new PokemonUpdateRequest(null, " ", null, null, "", null, null,
                null, null, null, null));

        assertNull(result.image());
        assertNull(result.description());
    }

    // --- If-Match ---

    @Test
    void shouldUpdateWhenIfMatchIsTheCurrentETagAndAnswerTheNewOne() {
        var entity = localmon(3);
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(entity));
        when(pokemonRepository.save(entity)).thenReturn(entity);

        var result = service.update(9001, at(3), patch("category", "Custom"));

        assertEquals("Custom", result.body().category());
        assertEquals(version(3), result.version(), "the mock repository does not bump the version itself");
        InOrder order = inOrder(pokemonRepository);
        order.verify(pokemonRepository).save(entity);
        order.verify(pokemonRepository).forceVersionIncrement(entity);
    }

    @Test
    void shouldRefuseAnUpdateWhenTheRecordChangedSinceItWasRead() {
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(localmon(4)));

        var error = assertThrows(PreconditionFailedException.class,
                () -> service.update(9001, at(3), patch("category", "Custom")));

        assertEquals(version(4), error.getCurrentVersion());
        verify(pokemonRepository, never()).save(any());
    }

    @Test
    void shouldFollowTheIfMatchComparisonRules() {
        var entity = localmon(3);
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(entity));
        when(pokemonRepository.save(entity)).thenReturn(entity);

        // Any version; one of several; no condition. (How If-Match becomes a condition is tested in EntityTagsTest.)
        assertDoesNotThrow(() -> service.update(9001, VersionCondition.any(), patch("category", "A")));
        assertDoesNotThrow(() -> service.update(9001, VersionCondition.oneOf(List.of("1-1", version(3))), patch("category", "B")));
        assertDoesNotThrow(() -> service.update(9001, null, patch("category", "C")));
        // A condition no version can meet (If-Match with only weak or malformed tags).
        assertThrows(PreconditionFailedException.class, () -> service.update(9001, VersionCondition.oneOf(List.of()), patch("category", "D")));
    }

    @Test
    void shouldRefuseAReplaceOrDeleteOfAChangedRecord() {
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(localmon(5)));

        assertThrows(PreconditionFailedException.class, () -> service.replace(9001, at(4),
                new PokemonReplaceRequest("localmon", null, null, null, null, null, null, null, null, null, null)));
        assertThrows(PreconditionFailedException.class, () -> service.delete(9001, at(4)));

        verify(pokemonRepository, never()).save(any());
        verify(pokemonRepository, never()).delete(any());
        verifyNoInteractions(tombstoneRepository);
    }

    @Test
    void shouldReplaceWithTheCurrentETagAndMoveToANewVersion() {
        var entity = localmon(5);
        entity.setStats(new ArrayList<>());
        entity.setSkills(new ArrayList<>());
        when(pokemonRepository.findById(9001)).thenReturn(Optional.of(entity));
        when(pokemonRepository.save(entity)).thenReturn(entity);

        service.replace(9001, at(5),
                new PokemonReplaceRequest("localmon", null, null, null, null, null, null, null, null, null, null));

        verify(pokemonRepository).forceVersionIncrement(entity);
    }

    @Test
    void shouldNotReplicateAnEnrichmentSentWithIfMatch() {
        when(pokemonRepository.existsById(25)).thenReturn(false);
        when(tombstoneRepository.existsById(25)).thenReturn(false);

        var error = assertThrows(PreconditionFailedException.class, () -> service.update(25, VersionCondition.any(),
                new PokemonUpdateRequest(null, null, null, null, null, null, null, null, null, "Kanto", null)));

        assertNull(error.getCurrentVersion());
        verifyNoInteractions(client, syncService);
    }

    // --- 404 for missing records ---

    @Test
    void shouldNotBringBackADeletedRecordThroughAnUpdate() {
        when(pokemonRepository.existsById(25)).thenReturn(false);
        when(tombstoneRepository.existsById(25)).thenReturn(true);

        var error = assertThrows(NoSuchElementException.class, () -> service.updatePokemon(25,
                new PokemonUpdateRequest(null, null, null, null, null, null, null, null, null, "Kanto", null)));

        assertTrue(error.getMessage().startsWith("Pokemon 25 was deleted from the local store"));
        assertInstanceOf(com.pokemon.bff.exception.PokemonDeletedException.class, error);
        verifyNoInteractions(client, syncService);
    }

    @Test
    void shouldAnswerNotFoundForAMissingRecordWhateverTheIfMatch() {
        when(pokemonRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.update(404, VersionCondition.oneOf(List.of("1-1")), patch("mass", 2.0)));
        assertThrows(NoSuchElementException.class, () -> service.replace(404, VersionCondition.oneOf(List.of("1-1")),
                new PokemonReplaceRequest("ghost", null, null, null, null, null, null, null, null, null, null)));
        assertThrows(NoSuchElementException.class, () -> service.delete(404, VersionCondition.oneOf(List.of("1-1"))));
    }

    // --- POST: the id may not belong to a PokeAPI Pokemon ---

    private static PokemonCreateRequest createRequest(int id, String name) {
        return new PokemonCreateRequest(id, name, null, null, null, null, List.of(), List.of(), null, null, null);
    }

    private static feign.FeignException notFound() {
        var request = feign.Request.create(feign.Request.HttpMethod.GET, "https://pokeapi.co/api/v2/x",
                java.util.Map.of(), null, java.nio.charset.StandardCharsets.UTF_8, null);
        return feign.FeignException.errorStatus("PokemonClient#find", feign.Response.builder().status(404)
                .reason("Not Found").request(request).headers(java.util.Map.of()).build());
    }

    @Test
    void shouldRefuseToCreateARecordWithTheIdOfAPokeApiPokemon() {
        when(pokemonRepository.existsById(25)).thenReturn(false);
        when(pokemonRepository.findByName("my-mon")).thenReturn(Optional.empty());
        when(client.findByNameOrId("my-mon")).thenThrow(notFound());
        when(client.findByNameOrId("25")).thenReturn(new com.pokemon.bff.client.dto.PokemonDetailsResponse(
                25, "pikachu", 4, 60, null, List.of(), List.of()));

        var error = assertThrows(InvalidFieldException.class, () -> service.createPokemon(createRequest(25, "my-mon")));

        assertEquals("id", error.getField());
        assertTrue(error.getMessage().contains("belongs to PokeAPI Pokemon pikachu"), error.getMessage());
        verify(pokemonRepository, never()).save(any());
        verifyNoInteractions(tombstoneRepository);
    }

    @Test
    void shouldCreateARecordWithAnIdPokeApiDoesNotKnow() {
        when(pokemonRepository.existsById(9001)).thenReturn(false);
        when(pokemonRepository.findByName("my-mon")).thenReturn(Optional.empty());
        when(client.findByNameOrId("my-mon")).thenThrow(notFound());
        when(client.findByNameOrId("9001")).thenThrow(notFound());
        when(pokemonRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(9001, service.createPokemon(createRequest(9001, "my-mon")).id());
    }

    @Test
    void shouldNotCreateWhenPokeApiCannotSayWhetherTheIdIsTaken() {
        when(pokemonRepository.existsById(9001)).thenReturn(false);
        when(pokemonRepository.findByName("my-mon")).thenReturn(Optional.empty());
        when(client.findByNameOrId("my-mon")).thenThrow(notFound());
        when(client.findByNameOrId("9001")).thenThrow(new RuntimeException("PokeAPI down"));

        assertThrows(RuntimeException.class, () -> service.createPokemon(createRequest(9001, "my-mon")));
        verify(pokemonRepository, never()).save(any());
    }

    // --- version ---

    @Test
    void shouldGiveARecreatedRecordADifferentVersion() {
        var before = localmon(0);
        var recreated = new PokemonEntity(9001, "localmon", null, null, null, null, CREATED.plusSeconds(60));
        recreated.setVersion(0L);

        assertNotEquals(before.versionTag(), recreated.versionTag());
    }
}
