package com.pokemon.bff.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.pokemon.bff.config.FeignConfig;
import com.pokemon.bff.service.PokeApiCache;
import feign.FeignException;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests of the real {@link PokemonClient} (Feign, decoder and error decoder as configured in
 * {@link FeignConfig}) and of the retry policy ({@link PokeApiRetrier} through {@link PokeApiCache}) against a
 * simulated PokeAPI. They check what the mocks in the other tests take for granted: the requests sent, the decoding
 * of PokeAPI's JSON, how HTTP failures become exceptions and when calls are retried.
 */
@SpringBootTest(classes = PokemonClientContractTest.ClientOnly.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.openfeign.client.config.default.connect-timeout=500",
                "spring.cloud.openfeign.client.config.default.read-timeout=1000",
                "pokeapi.retry.max-attempts=3",
                "pokeapi.retry.period=10ms",
                "pokeapi.retry.max-period=50ms",
                "pokeapi.retry.budget=1500ms"
        })
class PokemonClientContractTest {

    @RegisterExtension
    static WireMockExtension pokeApi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void pokeApiUrl(DynamicPropertyRegistry registry) {
        registry.add("pokeapi.url", () -> pokeApi.baseUrl() + "/api/v2");
    }

    @Autowired
    private PokemonClient client;

    @Autowired
    private PokeApiCache pokeApiCache;

    @Test
    void shouldRequestThePageWithOffsetAndLimit() {
        pokeApi.stubFor(get(urlPathEqualTo("/api/v2/pokemon"))
                .withQueryParam("offset", equalTo("40"))
                .withQueryParam("limit", equalTo("20"))
                .willReturn(okJson("""
                        {"count": 1351, "next": "n", "previous": "p",
                         "results": [{"name": "bulbasaur", "url": "https://pokeapi.co/api/v2/pokemon/1/"}]}
                        """)));

        var list = client.findAll(40, 20);

        assertEquals(1351, list.count());
        assertEquals("bulbasaur", list.results().getFirst().name());
        assertEquals("https://pokeapi.co/api/v2/pokemon/1/", list.results().getFirst().url());
    }

    @Test
    void shouldDecodeAPokemon() {
        pokeApi.stubFor(get("/api/v2/pokemon/charizard-mega-x").willReturn(okJson("""
                {"id": 10034, "name": "charizard-mega-x", "height": 17, "weight": 1105, "base_experience": 285,
                 "sprites": {"front_default": "mega-x.png", "back_default": "back.png"},
                 "abilities": [{"ability": {"name": "tough-claws", "url": "u-ability"}, "is_hidden": false, "slot": 1}],
                 "stats": [{"base_stat": 78, "effort": 0, "stat": {"name": "hp", "url": "u-hp"}}],
                 "species": {"name": "charizard", "url": "https://pokeapi.co/api/v2/pokemon-species/6/"}}
                """)));

        var pokemon = client.findByNameOrId("charizard-mega-x");

        assertEquals(10034, pokemon.id());
        assertEquals(17, pokemon.height());
        assertEquals(1105, pokemon.weight());
        assertEquals("mega-x.png", pokemon.sprites().frontDefault());
        assertEquals("tough-claws", pokemon.abilities().getFirst().ability().name());
        assertEquals(78, pokemon.stats().getFirst().baseStat());
        assertEquals("charizard", pokemon.speciesKey());
    }

    @Test
    void shouldDecodeASpecies() {
        pokeApi.stubFor(get("/api/v2/pokemon-species/lycanroc").willReturn(okJson("""
                {"id": 745, "name": "lycanroc",
                 "genera": [{"genus": "Wolf Pokemon", "language": {"name": "en", "url": "u"}}],
                 "flavor_text_entries": [{"flavor_text": "Howls\\nat the sun.", "language": {"name": "en", "url": "u"},
                                          "version": {"name": "sun", "url": "u"}}],
                 "evolution_chain": {"url": "https://pokeapi.co/api/v2/evolution-chain/372/"},
                 "varieties": [{"is_default": true, "pokemon": {"name": "lycanroc-midday", "url": "u1"}},
                               {"is_default": false, "pokemon": {"name": "lycanroc-midnight", "url": "u2"}}]}
                """)));

        var species = client.findSpeciesByNameOrId("lycanroc");

        assertEquals("Wolf Pokemon", species.genera().getFirst().genus());
        assertEquals("Howls\nat the sun.", species.flavorTextEntries().getFirst().flavorText());
        assertEquals("https://pokeapi.co/api/v2/evolution-chain/372/", species.evolutionChain().url());
        assertEquals("lycanroc-midday", species.defaultPokemonName().orElseThrow());
    }

    @Test
    void shouldDecodeAnEvolutionChain() {
        pokeApi.stubFor(get("/api/v2/evolution-chain/67").willReturn(okJson("""
                {"id": 67, "baby_trigger_item": null,
                 "chain": {"species": {"name": "eevee", "url": "u133"}, "evolution_details": [], "is_baby": false,
                           "evolves_to": [{"species": {"name": "vaporeon", "url": "u134"}, "evolves_to": []},
                                          {"species": {"name": "jolteon", "url": "u135"}, "evolves_to": []}]}}
                """)));

        var chain = client.findEvolutionChainById(67).chain();

        assertEquals("eevee", chain.species().name());
        assertEquals("jolteon", chain.evolvesTo().get(1).species().name());
    }

    @Test
    void shouldReportAMissingPokemonAsNotFoundWithoutRetrying() {
        pokeApi.stubFor(get("/api/v2/pokemon/missingno").willReturn(notFound()));

        assertThrows(FeignException.NotFound.class, () -> client.findByNameOrId("missingno"));
        pokeApi.verify(1, getRequestedFor(urlEqualTo("/api/v2/pokemon/missingno")));
    }

    @Test
    void shouldNotRetryInsideTheFeignClientButKeepTheStatus() {
        pokeApi.stubFor(get("/api/v2/pokemon/pikachu").willReturn(aResponse().withStatus(502)));

        var exception = assertThrows(RetryableException.class, () -> client.findByNameOrId("pikachu"));

        // the retry policy lives in PokeApiRetrier; Feign reports the transient failure once
        assertEquals(502, exception.status());
        pokeApi.verify(1, getRequestedFor(urlEqualTo("/api/v2/pokemon/pikachu")));
    }

    @Test
    void shouldReportATimeoutAsRetryableWithoutStatus() {
        pokeApi.stubFor(get("/api/v2/pokemon/slowpoke")
                .willReturn(okJson("{\"id\": 79, \"name\": \"slowpoke\"}").withFixedDelay(2_500)));

        var exception = assertThrows(RetryableException.class, () -> client.findByNameOrId("slowpoke"));

        assertEquals(-1, exception.status());
        pokeApi.verify(1, getRequestedFor(urlEqualTo("/api/v2/pokemon/slowpoke")));
    }

    // --- the retry policy over real HTTP (PokeApiCache + PokeApiRetrier) ---

    @Test
    void shouldRetryADirectCallAfterATransientFailure() {
        pokeApi.stubFor(get("/api/v2/pokemon/pikachu").inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(serviceUnavailable())
                .willSetStateTo("recovered"));
        pokeApi.stubFor(get("/api/v2/pokemon/pikachu").inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(okJson("{\"id\": 25, \"name\": \"pikachu\", \"height\": 4, \"weight\": 60}")));

        var pokemon = pokeApiCache.pokemon("pikachu", true);

        assertEquals(25, pokemon.id());
        pokeApi.verify(2, getRequestedFor(urlEqualTo("/api/v2/pokemon/pikachu")));
    }

    @Test
    void shouldGiveUpADirectCallAfterTheConfiguredAttempts() {
        pokeApi.stubFor(get("/api/v2/pokemon/pikachu").willReturn(aResponse().withStatus(502)));

        var exception = assertThrows(RetryableException.class, () -> pokeApiCache.pokemon("pikachu", true));

        assertEquals(502, exception.status());
        pokeApi.verify(3, getRequestedFor(urlEqualTo("/api/v2/pokemon/pikachu")));
    }

    @Test
    void shouldNotRetryPageEntries() {
        pokeApi.stubFor(get("/api/v2/pokemon/pikachu").willReturn(serviceUnavailable()));

        assertThrows(RetryableException.class, () -> pokeApiCache.pokemon("pikachu", false));

        pokeApi.verify(1, getRequestedFor(urlEqualTo("/api/v2/pokemon/pikachu")));
    }

    @Test
    void shouldNotRetryACallThatTimedOut() {
        pokeApi.stubFor(get("/api/v2/pokemon/slowpoke")
                .willReturn(okJson("{\"id\": 79, \"name\": \"slowpoke\"}").withFixedDelay(2_500)));

        assertThrows(RetryableException.class, () -> pokeApiCache.pokemon("slowpoke", true));

        // a second 1 s timeout would not fit the 1.5 s budget
        pokeApi.verify(1, getRequestedFor(urlEqualTo("/api/v2/pokemon/slowpoke")));
    }

    @Test
    // the pause is global by design (one retrier per application): start the next tests with a fresh one
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void shouldStopCallingPokeApiWhileARateLimitIsInForce() {
        pokeApi.stubFor(get("/api/v2/pokemon/pikachu")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));

        var first = assertThrows(PokeApiRateLimitException.class, () -> pokeApiCache.pokemon("pikachu", true));
        var second = assertThrows(PokeApiRateLimitException.class, () -> pokeApiCache.species("pikachu", true));

        assertTrue(first.upstreamCalled());
        assertFalse(second.upstreamCalled());
        assertTrue(second.retryAfter().isAfter(java.time.Instant.now().plusSeconds(25)));
        pokeApi.verify(1, getRequestedFor(urlEqualTo("/api/v2/pokemon/pikachu")));
        pokeApi.verify(0, getRequestedFor(urlEqualTo("/api/v2/pokemon-species/pikachu")));
    }

    /**
     * Only what the PokeAPI integration needs: the Feign client, the retry policy and PokeApiCache (without
     * {@code @EnableCaching}, so every call reaches the simulated PokeAPI). No database, no web server.
     */
    @Configuration
    @ImportAutoConfiguration({JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
            FeignAutoConfiguration.class})
    @Import({FeignConfig.class, PokeApiStatus.class, PokeApiRetrier.class, PokeApiCache.class})
    static class ClientOnly {
    }
}
