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

/** Unit tests of the PokeAPI resource: enumeration, detail, degradation, rate limit and background replication. */
@ExtendWith(MockitoExtension.class)
class PokeApiPokemonServiceTest {
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
    private PokemonCatalogService catalogService;

    private PokeApiPokemonService service;

    @BeforeEach
    void setUp() {
        // Runs the per-item tasks on the calling thread so the tests stay deterministic.
        // Real PokeApiCache over the mocked client (no Spring proxy, so no caching here) and a throttle that always
        // lets the sync through.
        var pokeApi = new PokeApiCache(client);
        service = new PokeApiPokemonService(pokeApi, syncService, new PokemonSyncThrottle(new NoOpCacheManager()),
                pokemonRepository, catalogService, Runnable::run);
    }

    @Test
    void shouldReturnDetailedViewWithDescriptionStatsAndLineage() {
        Faker faker = new Faker();
        int pokemonId = faker.number().numberBetween(1, 500);
        String imageUrl = faker.internet().url();
        String hpStatName = faker.lorem().word().toLowerCase();
        int hpStatValue = faker.number().numberBetween(30, 150);
        int attackStatValue = faker.number().numberBetween(30, 150);
        int defenseStatValue = faker.number().numberBetween(30, 150);
        String japaneseDescription = faker.lorem().sentence();
        String englishDescriptionRaw = faker.lorem().sentence() + "\n" + faker.lorem().sentence();
        String expectedEnglishDescription = englishDescriptionRaw.replace('\n', ' ').replaceAll("\\s+", " ").trim();

        when(client.findByNameOrId(POKEMON_BULBASAUR)).thenReturn(new PokemonDetailsResponse(
                pokemonId,
                POKEMON_BULBASAUR,
                7,
                69,
                new PokemonDetailsResponse.Sprites(imageUrl),
                List.of(),
                List.of(
                        new PokemonDetailsResponse.StatSlot(hpStatValue, new PokemonDetailsResponse.Stat(hpStatName, faker.internet().url())),
                        new PokemonDetailsResponse.StatSlot(attackStatValue, new PokemonDetailsResponse.Stat("attack", faker.internet().url())),
                        new PokemonDetailsResponse.StatSlot(defenseStatValue, new PokemonDetailsResponse.Stat("defense", faker.internet().url())))));
        when(client.findSpeciesByNameOrId(POKEMON_BULBASAUR)).thenReturn(new PokemonSpeciesResponse(
                List.of(new PokemonSpeciesResponse.Genus("Seed Pokemon",
                        new PokemonSpeciesResponse.Language("en"))),
                List.of(
                        new PokemonSpeciesResponse.FlavorTextEntry(japaneseDescription,
                                new PokemonSpeciesResponse.Language("ja")),
                        new PokemonSpeciesResponse.FlavorTextEntry(englishDescriptionRaw,
                                new PokemonSpeciesResponse.Language("en"))),
                new PokemonSpeciesResponse.EvolutionChain("https://pokeapi.co/api/v2/evolution-chain/1/")));
        when(client.findEvolutionChainById(1)).thenReturn(new EvolutionChainResponse(
                new EvolutionChainResponse.ChainLink(
                        new PokemonReference(POKEMON_BULBASAUR, "u1"),
                        List.of(new EvolutionChainResponse.ChainLink(
                                new PokemonReference(POKEMON_IVYSAUR, "u2"),
                                List.of(new EvolutionChainResponse.ChainLink(
                                        new PokemonReference(POKEMON_VENUSAUR, "u3"),
                                        List.of())))))));

        var detail = service.findByNameOrId("Bulbasaur");

        assertEquals(pokemonId, detail.id());
        assertEquals(POKEMON_BULBASAUR, detail.name());
        assertEquals(imageUrl, detail.image());
        assertEquals(0.7, detail.height());
        assertEquals(6.9, detail.mass());
        assertEquals(3, detail.stats().size());
        assertEquals(hpStatName, detail.stats().getFirst().name());
        assertEquals(hpStatValue, detail.stats().getFirst().value());
        assertEquals(expectedEnglishDescription, detail.description());
        assertNotNull(detail.evolutionLineage());
        assertEquals(POKEMON_BULBASAUR, detail.evolutionLineage().name());
        assertEquals(POKEMON_IVYSAUR, detail.evolutionLineage().evolvesTo().getFirst().name());
        assertEquals(POKEMON_VENUSAUR, detail.evolutionLineage().evolvesTo().getFirst().evolvesTo().getFirst().name());
        verify(syncService).sync(any(PokemonSnapshot.class));
    }

    @Test
    void shouldBuildPageFromCatalogItems() {
        when(catalogService.fetchReferences(0, 2)).thenReturn(new PokemonListResponse(3, List.of(
                new PokemonReference("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"),
                new PokemonReference("charmander", "https://pokeapi.co/api/v2/pokemon/4/"))));
        var bulbasaur = new PokemonItem(1, "bulbasaur", "img-1", "Seed Pokemon", 6.9,
                List.of(new Skill("overgrow", "u-ability-1")));
        var charmander = new PokemonItem(4, "charmander", "img-4", "Lizard Pokemon", 8.5,
                List.of(new Skill("blaze", "u-ability-2")));
        when(catalogService.fetchItem(eq("bulbasaur"), any())).thenReturn(bulbasaur);
        when(catalogService.fetchItem(eq("charmander"), any())).thenReturn(charmander);

        var page = service.findPage(0, 2);

        assertEquals(List.of(bulbasaur, charmander), page.content());
        assertEquals(0, page.page());
        assertEquals(2, page.size());
        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
    }

    @Test
    void shouldUseOffsetFromPageAndSize() {
        when(catalogService.fetchReferences(40, 20)).thenReturn(new PokemonListResponse(41, List.of()));

        var page = service.findPage(2, 20);

        assertTrue(page.content().isEmpty());
        assertEquals(3, page.totalPages());
    }

    @Test
    void shouldReturnUnavailableItemWhenOnePokemonFailsUpstream() {
        when(catalogService.fetchReferences(0, 2)).thenReturn(new PokemonListResponse(2, List.of(
                new PokemonReference("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"),
                new PokemonReference("missingno", "https://pokeapi.co/api/v2/pokemon/151/"))));
        var bulbasaur = new PokemonItem(1, "bulbasaur", "img-1", "Seed Pokemon", 6.9, List.of());
        when(catalogService.fetchItem(eq("bulbasaur"), any())).thenReturn(bulbasaur);
        when(catalogService.fetchItem(eq("missingno"), any())).thenThrow(new RuntimeException("upstream 500"));

        var page = service.findPage(0, 2);

        assertEquals(2, page.content().size());
        assertEquals(bulbasaur, page.content().getFirst());
        var failed = page.content().get(1);
        assertFalse(failed.available());
        assertEquals(151, failed.id());
        assertEquals("missingno", failed.name());
        assertNull(failed.image());
        assertNull(failed.mass());
        assertTrue(failed.skills().isEmpty());
    }

    @Test
    void shouldReportWhenToReloadAPageWhoseEntriesWereRateLimited() {
        when(catalogService.fetchReferences(0, 2)).thenReturn(new PokemonListResponse(2, List.of(
                new PokemonReference("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"),
                new PokemonReference("ivysaur", "https://pokeapi.co/api/v2/pokemon/2/"))));
        when(catalogService.fetchItem(eq("bulbasaur"), any())).thenReturn(new PokemonItem(1, "bulbasaur", "img-1", "Seed Pokemon", 6.9, List.of()));
        when(catalogService.fetchItem(eq("ivysaur"), any())).thenThrow(new PokeApiRateLimitException(
                Instant.now().plusSeconds(15), false, null));

        var page = service.findPage(0, 2);

        assertTrue(page.content().getFirst().available());
        assertFalse(page.content().get(1).available());
        assertTrue(page.retryAfterSeconds() >= 14 && page.retryAfterSeconds() <= 15, "was " + page.retryAfterSeconds());
    }

    @Test
    void shouldReportWhenToReloadAPageWhoseCategoriesWereRateLimited() {
        when(catalogService.fetchReferences(0, 1)).thenReturn(new PokemonListResponse(1, List.of(
                new PokemonReference("ivysaur", "https://pokeapi.co/api/v2/pokemon/2/"))));
        when(catalogService.fetchItem(eq("ivysaur"), any())).thenAnswer(invocation -> {
            java.util.function.Consumer<PokeApiRateLimitException> onRateLimited = invocation.getArgument(1);
            onRateLimited.accept(new PokeApiRateLimitException(Instant.now().plusSeconds(15), false, null));
            return PokemonItem.withoutCategory(2, "ivysaur", "img-2", 13.0, List.of());
        });

        var page = service.findPage(0, 1);

        var entry = page.content().getFirst();
        assertTrue(entry.available());
        assertFalse(entry.categoryAvailable());
        assertEquals("img-2", entry.image());
        assertTrue(page.retryAfterSeconds() >= 14 && page.retryAfterSeconds() <= 15, "was " + page.retryAfterSeconds());
    }

    @Test
    void shouldNotReportRetryAfterForOtherFailures() {
        when(catalogService.fetchReferences(0, 1)).thenReturn(new PokemonListResponse(1, List.of(
                new PokemonReference("ivysaur", "https://pokeapi.co/api/v2/pokemon/2/"))));
        when(catalogService.fetchItem(eq("ivysaur"), any())).thenThrow(new RuntimeException("upstream 503"));

        assertNull(service.findPage(0, 1).retryAfterSeconds());
    }

    @Test
    void shouldPropagateFailureWhenPokemonListCannotBeLoaded() {
        when(catalogService.fetchReferences(0, 20)).thenThrow(new IllegalStateException("upstream down"));

        assertThrows(IllegalStateException.class, () -> service.findPage(0, 20));
    }

    @Test
    void shouldRejectInvalidPaginationBeforeCallingUpstream() {
        assertThrows(IllegalArgumentException.class, () -> service.findPage(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.findPage(0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.findPage(0, 101));
        verifyNoInteractions(catalogService);
    }

    @Test
    void shouldRejectPageWhoseOffsetOverflows() {
        var error = assertThrows(IllegalArgumentException.class, () -> service.findPage(50_000_000, 100));

        assertEquals("page is too large for the requested size", error.getMessage());
        verifyNoInteractions(catalogService);
    }

    @Test
    void shouldReturnDetailEvenWhenLocalSyncFails() {
        when(client.findByNameOrId("ditto")).thenReturn(new PokemonDetailsResponse(
                132, "ditto", 3, 40, null, List.of(), List.of()));
        when(client.findSpeciesByNameOrId("ditto")).thenReturn(new PokemonSpeciesResponse(
                List.of(), List.of(),
                new PokemonSpeciesResponse.EvolutionChain("https://pokeapi.co/api/v2/evolution-chain/66/")));
        when(client.findEvolutionChainById(66)).thenReturn(null);
        doThrow(new RuntimeException("database down")).when(syncService).sync(any());

        var detail = service.findByNameOrId("ditto");

        assertEquals(132, detail.id());
    }

    @Test
    void shouldThrowWhenPokemonIdentifierIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> service.findByNameOrId("   "));
        assertThrows(IllegalArgumentException.class, () -> service.findByNameOrId(null));
    }

    @Test
    void shouldReturnFallbackLineageWhenEvolutionChainIsNull() {
        when(client.findByNameOrId("eevee")).thenReturn(new PokemonDetailsResponse(
                133, "eevee", 3, 65, null, List.of(), null));
        when(client.findSpeciesByNameOrId("eevee")).thenReturn(new PokemonSpeciesResponse(
                List.of(),
                List.of(new PokemonSpeciesResponse.FlavorTextEntry("text",
                        new PokemonSpeciesResponse.Language("en"))),
                new PokemonSpeciesResponse.EvolutionChain("https://pokeapi.co/api/v2/evolution-chain/67/")));
        when(client.findEvolutionChainById(67)).thenReturn(null);

        var detail = service.findByNameOrId("eevee");

        assertEquals("eevee", detail.evolutionLineage().name());
        assertEquals(0, detail.evolutionLineage().evolvesTo().size());
        assertEquals(0, detail.stats().size());
    }

    @Test
    void shouldDegradeLineageWhenEvolutionChainUrlIsMissing() {
        when(client.findByNameOrId("mew")).thenReturn(new PokemonDetailsResponse(
                151, "mew", 4, 40, new PokemonDetailsResponse.Sprites("mew.png"), List.of(), List.of()));
        when(client.findSpeciesByNameOrId("mew")).thenReturn(new PokemonSpeciesResponse(
                List.of(),
                List.of(new PokemonSpeciesResponse.FlavorTextEntry("New species",
                        new PokemonSpeciesResponse.Language("en"))),
                null));

        var detail = service.findByNameOrId("mew");

        assertFalse(detail.evolutionAvailable());
        assertEquals("mew", detail.evolutionLineage().name());
        assertTrue(detail.evolutionLineage().evolvesTo().isEmpty());
        assertEquals("mew.png", detail.image());
        assertEquals("New species", detail.description());
    }

    @Test
    void shouldDegradeLineageWhenEvolutionChainUrlIsInvalid() {
        when(client.findByNameOrId("mewtwo")).thenReturn(new PokemonDetailsResponse(
                150, "mewtwo", 20, 1220, null, List.of(), List.of()));
        when(client.findSpeciesByNameOrId("mewtwo")).thenReturn(new PokemonSpeciesResponse(
                List.of(),
                List.of(),
                new PokemonSpeciesResponse.EvolutionChain("invalid-url")));

        var detail = service.findByNameOrId("mewtwo");

        assertFalse(detail.evolutionAvailable());
        assertEquals("mewtwo", detail.evolutionLineage().name());
        verify(client, never()).findEvolutionChainById(anyInt());
    }

    @Test
    void shouldKeepImageStatsAndDescriptionWhenEvolutionChainFails() {
        when(client.findByNameOrId("pikachu")).thenReturn(new PokemonDetailsResponse(
                25, "pikachu", 4, 60, new PokemonDetailsResponse.Sprites("pikachu.png"), List.of(),
                List.of(new PokemonDetailsResponse.StatSlot(35, new PokemonDetailsResponse.Stat("hp", "u")))));
        when(client.findSpeciesByNameOrId("pikachu")).thenReturn(new PokemonSpeciesResponse(
                List.of(),
                List.of(new PokemonSpeciesResponse.FlavorTextEntry("Electric mouse.",
                        new PokemonSpeciesResponse.Language("en"))),
                new PokemonSpeciesResponse.EvolutionChain("https://pokeapi.co/api/v2/evolution-chain/10/")));
        when(client.findEvolutionChainById(10)).thenThrow(new RuntimeException("upstream 503"));

        var detail = service.findByNameOrId("pikachu");

        assertFalse(detail.evolutionAvailable());
        assertEquals("pikachu", detail.evolutionLineage().name());
        assertEquals("pikachu.png", detail.image());
        assertEquals(35, detail.stats().getFirst().value());
        assertEquals("Electric mouse.", detail.description());
    }

    @Test
    void shouldTellWhenToAskAgainWhenTheSpeciesIsRateLimited() {
        when(client.findByNameOrId("pikachu")).thenReturn(new PokemonDetailsResponse(
                25, "pikachu", 4, 60, new PokemonDetailsResponse.Sprites("pikachu.png"), List.of(), List.of()));
        when(client.findSpeciesByNameOrId("pikachu")).thenThrow(new PokeApiRateLimitException(
                Instant.now().plusSeconds(20), true, null));

        var detail = service.findByNameOrId("pikachu");

        assertFalse(detail.descriptionAvailable());
        assertFalse(detail.evolutionAvailable());
        assertEquals("pikachu.png", detail.image());
        assertTrue(detail.retryAfterSeconds() >= 19 && detail.retryAfterSeconds() <= 20, "was " + detail.retryAfterSeconds());
    }

    @Test
    void shouldNotReportRetryAfterWhenTheDetailFailsForOtherReasons() {
        when(client.findByNameOrId("pikachu")).thenReturn(new PokemonDetailsResponse(
                25, "pikachu", 4, 60, null, List.of(), List.of()));
        when(client.findSpeciesByNameOrId("pikachu")).thenThrow(new RuntimeException("upstream 503"));

        assertNull(service.findByNameOrId("pikachu").retryAfterSeconds());
    }

    @Test
    void shouldResolveSpeciesOfAlternateFormThroughItsSpeciesName() {
        when(client.findByNameOrId("charizard-mega-x")).thenReturn(new PokemonDetailsResponse(
                10034, "charizard-mega-x", 17, 1105, new PokemonDetailsResponse.Sprites("mega-x.png"), List.of(),
                List.of(), new PokemonReference("charizard", "https://pokeapi.co/api/v2/pokemon-species/6/")));
        when(client.findSpeciesByNameOrId("charizard")).thenReturn(new PokemonSpeciesResponse(
                List.of(),
                List.of(new PokemonSpeciesResponse.FlavorTextEntry("Spits fire.",
                        new PokemonSpeciesResponse.Language("en"))),
                new PokemonSpeciesResponse.EvolutionChain("https://pokeapi.co/api/v2/evolution-chain/2/")));
        when(client.findEvolutionChainById(2)).thenReturn(new EvolutionChainResponse(
                new EvolutionChainResponse.ChainLink(new PokemonReference("charmander", "u4"),
                        List.of(new EvolutionChainResponse.ChainLink(new PokemonReference("charmeleon", "u5"),
                                List.of(new EvolutionChainResponse.ChainLink(new PokemonReference("charizard", "u6"),
                                        List.of())))))));

        var detail = service.findByNameOrId("charizard-mega-x");

        assertEquals(10034, detail.id());
        assertEquals("charizard-mega-x", detail.name());
        assertEquals("Spits fire.", detail.description());
        assertTrue(detail.evolutionAvailable());
        assertEquals("charmander", detail.evolutionLineage().name());
        verify(client, never()).findSpeciesByNameOrId("charizard-mega-x");
    }

    @Test
    void shouldResolveSpeciesNameToItsDefaultPokemon() {
        when(client.findByNameOrId("lycanroc")).thenThrow(notFound("/pokemon/lycanroc"));
        when(client.findSpeciesByNameOrId("lycanroc")).thenReturn(new PokemonSpeciesResponse(
                List.of(),
                List.of(new PokemonSpeciesResponse.FlavorTextEntry("Howls at the sun.",
                        new PokemonSpeciesResponse.Language("en"))),
                new PokemonSpeciesResponse.EvolutionChain("https://pokeapi.co/api/v2/evolution-chain/372/"),
                List.of(new PokemonSpeciesResponse.Variety(false, new PokemonReference("lycanroc-midnight", "u")),
                        new PokemonSpeciesResponse.Variety(true, new PokemonReference("lycanroc-midday", "u")))));
        when(client.findByNameOrId("lycanroc-midday")).thenReturn(new PokemonDetailsResponse(
                745, "lycanroc-midday", 8, 250, new PokemonDetailsResponse.Sprites("midday.png"), List.of(),
                List.of(), new PokemonReference("lycanroc", "https://pokeapi.co/api/v2/pokemon-species/745/")));
        when(client.findEvolutionChainById(372)).thenReturn(new EvolutionChainResponse(
                new EvolutionChainResponse.ChainLink(new PokemonReference("rockruff", "u744"),
                        List.of(new EvolutionChainResponse.ChainLink(new PokemonReference("lycanroc", "u745"),
                                List.of())))));

        var detail = service.findByNameOrId("Lycanroc");

        assertEquals(745, detail.id());
        assertEquals("lycanroc-midday", detail.name());
        assertEquals("lycanroc", detail.species());
        assertEquals("Howls at the sun.", detail.description());
        assertEquals("rockruff", detail.evolutionLineage().name());
        // the species loaded for the fallback is reused, not requested again
        verify(client, times(1)).findSpeciesByNameOrId("lycanroc");
    }

    @Test
    void shouldReturnNotFoundWhenNeitherPokemonNorSpeciesExists() {
        var pokemonNotFound = notFound("/pokemon/missingno");
        when(client.findByNameOrId("missingno")).thenThrow(pokemonNotFound);
        when(client.findSpeciesByNameOrId("missingno")).thenThrow(notFound("/pokemon-species/missingno"));

        // The client layer reports "not in PokeAPI" without the HTTP library's exception.
        var thrown = assertThrows(PokeApiNotFoundException.class, () -> service.findByNameOrId("missingno"));

        assertSame(pokemonNotFound, thrown.getCause());
    }

    @Test
    void shouldKeepImageAndStatsWhenSpeciesFails() {
        when(client.findByNameOrId("pikachu")).thenReturn(new PokemonDetailsResponse(
                25, "pikachu", 4, 60, new PokemonDetailsResponse.Sprites("pikachu.png"), List.of(),
                List.of(new PokemonDetailsResponse.StatSlot(35, new PokemonDetailsResponse.Stat("hp", "u")))));
        when(client.findSpeciesByNameOrId("pikachu")).thenThrow(new RuntimeException("upstream 503"));

        var detail = service.findByNameOrId("pikachu");

        assertFalse(detail.descriptionAvailable());
        assertFalse(detail.evolutionAvailable());
        assertTrue(detail.partial());
        assertNull(detail.description());
        assertEquals("pikachu.png", detail.image());
        assertEquals(35, detail.stats().getFirst().value());
        assertEquals("pikachu", detail.evolutionLineage().name());
        verify(client, never()).findEvolutionChainById(anyInt());
    }

    @Test
    void shouldIncludeCategoryAndSkillsShownOnTheListCard() {
        when(client.findByNameOrId("bulbasaur")).thenReturn(new PokemonDetailsResponse(
                1, "bulbasaur", 7, 69, new PokemonDetailsResponse.Sprites("img-1"),
                List.of(new PokemonDetailsResponse.AbilitySlot(new PokemonDetailsResponse.Ability("overgrow", "u-1"))),
                List.of()));
        when(client.findSpeciesByNameOrId("bulbasaur")).thenReturn(new PokemonSpeciesResponse(
                List.of(new PokemonSpeciesResponse.Genus("Seed Pokemon", new PokemonSpeciesResponse.Language("en"))),
                List.of(), null));

        var detail = service.findByNameOrId("bulbasaur");

        assertEquals("Seed Pokemon", detail.category());
        assertEquals(List.of(new Skill("overgrow", "u-1")), detail.skills());
    }

    @Test
    void shouldMapBranchingEvolutionChain() {
        when(client.findByNameOrId("eevee")).thenReturn(new PokemonDetailsResponse(
                133, "eevee", 3, 65, null, List.of(), List.of()));
        when(client.findSpeciesByNameOrId("eevee")).thenReturn(new PokemonSpeciesResponse(
                List.of(), List.of(),
                new PokemonSpeciesResponse.EvolutionChain("https://pokeapi.co/api/v2/evolution-chain/67/")));
        when(client.findEvolutionChainById(67)).thenReturn(new EvolutionChainResponse(
                new EvolutionChainResponse.ChainLink(new PokemonReference("eevee", "u133"), List.of(
                        new EvolutionChainResponse.ChainLink(new PokemonReference("vaporeon", "u134"), List.of()),
                        new EvolutionChainResponse.ChainLink(new PokemonReference("jolteon", "u135"), List.of()),
                        new EvolutionChainResponse.ChainLink(new PokemonReference("flareon", "u136"), List.of())))));

        var lineage = service.findByNameOrId("eevee").evolutionLineage();

        assertEquals("eevee", lineage.name());
        assertEquals(List.of("vaporeon", "jolteon", "flareon"),
                lineage.evolvesTo().stream().map(EvolutionNode::name).toList());
        assertTrue(lineage.evolvesTo().stream().allMatch(node -> node.evolvesTo().isEmpty()));
    }

    @Test
    void shouldReturnNullDescriptionWhenNoEnglishFlavorTextFound() {
        when(client.findByNameOrId("snorlax")).thenReturn(new PokemonDetailsResponse(
                143, "snorlax", 21, 4600, null, List.of(), List.of()));
        when(client.findSpeciesByNameOrId("snorlax")).thenReturn(new PokemonSpeciesResponse(
                List.of(),
                List.of(new PokemonSpeciesResponse.FlavorTextEntry("descripcion",
                        new PokemonSpeciesResponse.Language("es"))),
                new PokemonSpeciesResponse.EvolutionChain("https://pokeapi.co/api/v2/evolution-chain/143/")));
        when(client.findEvolutionChainById(143)).thenReturn(new EvolutionChainResponse(
                new EvolutionChainResponse.ChainLink(new PokemonReference("snorlax", "u-snorlax"), null)));

        var detail = service.findByNameOrId("snorlax");

        assertNull(detail.description());
        assertEquals("snorlax", detail.evolutionLineage().name());
        assertEquals(0, detail.evolutionLineage().evolvesTo().size());
    }

    @Test
    void shouldShowProprietaryFieldsOnThePokeApiDetail() {
        stubPikachuInPokeApi("pikachu");
        var stored = replicatedPikachu();
        var metadata = new PokemonMetadataEntity();
        metadata.setPokemon(stored);
        metadata.setLocalizedName("Pikachu (ES)");
        metadata.setRegion("Kanto");
        stored.setMetadata(metadata);
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(stored));

        var detail = service.findByNameOrId("pikachu");

        assertEquals(new PokemonMetadata("Pikachu (ES)", "Kanto", null), detail.metadata());
    }

    @Test
    void shouldServeThePokeApiDetailEvenIfTheLocalStoreIsDown() {
        stubPikachuInPokeApi("pikachu");
        when(pokemonRepository.findById(25)).thenThrow(new IllegalStateException("database down"));

        var detail = service.findByNameOrId("pikachu");

        assertEquals(25, detail.id());
        assertNull(detail.metadata());
    }

    // --- User Story 03: replication and proprietary fields ---

    private PokemonEntity replicatedPikachu() {
        var entity = new PokemonEntity(25, "pikachu", "pikachu.png", 0.4, 6.0, "Electric mouse.", Instant.parse("2026-10-01T00:00:00Z"));
        entity.setCategory("Mouse Pokemon");
        return entity;
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
