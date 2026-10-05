package com.pokemon.bff.controller;

import com.pokemon.bff.client.PokeApiNotFoundException;
import com.pokemon.bff.client.PokeApiRateLimitException;
import com.pokemon.bff.dto.EvolutionNode;
import com.pokemon.bff.dto.PokemonDetail;
import com.pokemon.bff.dto.PokemonItem;
import com.pokemon.bff.dto.PokemonMetadata;
import com.pokemon.bff.dto.PokemonPage;
import com.pokemon.bff.dto.PokemonStat;
import com.pokemon.bff.dto.Skill;
import com.pokemon.bff.service.PokeApiPokemonService;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests of the read-only PokeAPI resource: parameter binding, status codes, HTTP caching and the JSON
 * contract of success and error responses.
 */
@WebMvcTest(PokemonController.class)
class PokemonControllerWebMvcTest {
    private static final String BASE_URL = "/api/v1/pokemons";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PokeApiPokemonService service;

    private static PokemonPage page(PokemonItem... items) {
        return new PokemonPage(List.of(items), 0, 20, 1302, 66);
    }

    private static PokemonDetail eevee() {
        return new PokemonDetail(133, "eevee", "eevee", "eevee.png", 0.3, 6.5, "Evolution Pokemon",
                List.of(new Skill("run-away", "u1")),
                List.of(new PokemonStat("hp", 55), new PokemonStat("speed", 55)),
                "It has the ability to alter its body.", true,
                new EvolutionNode("eevee", List.of(new EvolutionNode("vaporeon", List.of()),
                        new EvolutionNode("jolteon", List.of()))), true);
    }

    // --- list ---

    @Test
    void shouldReturnPageWithImageCategoryMassAndSkills() throws Exception {
        when(service.findPage(0, 20)).thenReturn(page(
                new PokemonItem(1, "bulbasaur", "img-1", "Seed Pokemon", 6.9, List.of(new Skill("overgrow", "u-1"))),
                PokemonItem.unavailable(4, "charmander")));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1302))
                .andExpect(jsonPath("$.totalPages").value(66))
                .andExpect(jsonPath("$.content[0].image").value("img-1"))
                .andExpect(jsonPath("$.content[0].category").value("Seed Pokemon"))
                .andExpect(jsonPath("$.content[0].mass").value(6.9))
                .andExpect(jsonPath("$.content[0].skills[0].name").value("overgrow"))
                .andExpect(jsonPath("$.content[0].available").value(true))
                .andExpect(jsonPath("$.content[0].sprite").doesNotExist())
                .andExpect(jsonPath("$.content[1].available").value(false))
                .andExpect(jsonPath("$.content[1].name").value("charmander"));
    }

    @Test
    void shouldReturnProblemDetailForInvalidPagination() throws Exception {
        when(service.findPage(0, 101)).thenThrow(new IllegalArgumentException("size must be between 1 and 100"));

        mockMvc.perform(get(BASE_URL).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"))
                .andExpect(jsonPath("$.instance").value(BASE_URL));
    }

    @Test
    void shouldReturnProblemDetailForNonNumericPage() throws Exception {
        mockMvc.perform(get(BASE_URL).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(service);
    }

    // --- detail ---

    @Test
    void shouldReturnDetailWithEverythingTheCardShowsPlusLineage() throws Exception {
        when(service.findByNameOrId("eevee")).thenReturn(eevee());

        mockMvc.perform(get(BASE_URL + "/eevee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(133))
                .andExpect(jsonPath("$.image").value("eevee.png"))
                .andExpect(jsonPath("$.height").value(0.3))
                .andExpect(jsonPath("$.mass").value(6.5))
                .andExpect(jsonPath("$.category").value("Evolution Pokemon"))
                .andExpect(jsonPath("$.skills[0].name").value("run-away"))
                .andExpect(jsonPath("$.stats.length()").value(2))
                .andExpect(jsonPath("$.stats[1].name").value("speed"))
                .andExpect(jsonPath("$.description").value("It has the ability to alter its body."))
                .andExpect(jsonPath("$.evolutionLineage.name").value("eevee"))
                .andExpect(jsonPath("$.evolutionLineage.evolvesTo[1].name").value("jolteon"))
                .andExpect(jsonPath("$.evolutionAvailable").value(true))
                .andExpect(jsonPath("$.descriptionAvailable").value(true))
                .andExpect(jsonPath("$.species").value("eevee"))
                .andExpect(jsonPath("$.coreStats").doesNotExist())
                .andExpect(jsonPath("$.partial").doesNotExist());
    }

    @Test
    void shouldShowProprietaryFieldsMergedIntoThePokeApiDetail() throws Exception {
        when(service.findByNameOrId("eevee")).thenReturn(eevee().withMetadata(new PokemonMetadata("Evoli", "Kanto", null)));

        mockMvc.perform(get(BASE_URL + "/eevee"))
                .andExpect(jsonPath("$.metadata.localizedName").value("Evoli"))
                .andExpect(jsonPath("$.metadata.region").value("Kanto"))
                .andExpect(jsonPath("$.metadata.classificationTag").doesNotExist());
    }

    @Test
    void shouldOmitTheMetadataBlockWhenNothingWasAddedLocally() throws Exception {
        when(service.findByNameOrId("eevee")).thenReturn(eevee());

        mockMvc.perform(get(BASE_URL + "/eevee"))
                .andExpect(jsonPath("$.metadata").doesNotExist());
    }

    @Test
    void shouldReturnNotFoundWhenPokemonDoesNotExistUpstream() throws Exception {
        when(service.findByNameOrId("missingno")).thenThrow(new PokeApiNotFoundException("not in PokeAPI",
                upstreamError(404, Map.of())));

        mockMvc.perform(get(BASE_URL + "/missingno"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("The requested Pokemon does not exist in PokeAPI"));
    }

    @Test
    void shouldBeReadOnly() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("{\"id\":1,\"name\":\"x\"}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(BASE_URL + "/25"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(service);
    }

    // --- HTTP caching ---

    @Test
    void shouldMakeClientsRevalidateCompleteResponsesWithTheEtagByDefault() throws Exception {
        when(service.findByNameOrId("eevee")).thenReturn(eevee());

        // no-cache by default: the browser always revalidates, so a card and its detail never diverge
        var first = mockMvc.perform(get(BASE_URL + "/eevee"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get(BASE_URL + "/eevee").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    void shouldForbidCachingPartialResponses() throws Exception {
        when(service.findByNameOrId("pikachu")).thenReturn(new PokemonDetail(25, "pikachu", "pikachu.png", 0.4, 6.0,
                List.of(), null, new EvolutionNode("pikachu", List.of()), false));
        when(service.findPage(0, 20)).thenReturn(page(PokemonItem.unavailable(4, "charmander")));

        mockMvc.perform(get(BASE_URL + "/pikachu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evolutionAvailable").value(false))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
        mockMvc.perform(get(BASE_URL))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    // --- upstream failures ---

    @Test
    void shouldReturnBadGatewayWhenPokeApiFails() throws Exception {
        when(service.findPage(0, 20)).thenThrow(upstreamError(500, Map.of()));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.title").value("Upstream failure"));
    }

    @Test
    void shouldReturnBadGatewayWhenPokeApiKeepsFailingAfterRetries() throws Exception {
        when(service.findPage(0, 20)).thenThrow(retryable(503, null));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Upstream failure"));
    }

    @Test
    void shouldReturnGatewayTimeoutWhenPokeApiDoesNotRespond() throws Exception {
        when(service.findPage(0, 20)).thenThrow(retryable(-1, null));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504));
    }

    @Test
    void shouldReturnServiceUnavailableWithRetryAfterWhenPokeApiRateLimits() throws Exception {
        when(service.findPage(0, 20)).thenThrow(retryable(429, System.currentTimeMillis() + 10_000));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "10"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Upstream rate limit"));
    }

    @Test
    void shouldUseDefaultRetryAfterWhenPokeApiGivesNone() throws Exception {
        when(service.findByNameOrId("pikachu")).thenThrow(upstreamError(429, Map.of()));

        mockMvc.perform(get(BASE_URL + "/pikachu"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"));
    }

    @Test
    void shouldTellWhenToReloadAPageWhoseEntriesWereRateLimited() throws Exception {
        when(service.findPage(0, 20)).thenReturn(new PokemonPage(List.of(PokemonItem.unavailable(4, "charmander")),
                0, 20, 1, 1, 12));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retryAfterSeconds").value(12))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "12"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void shouldTellWhenToAskAgainForADetailDegradedByTheRateLimit() throws Exception {
        when(service.findByNameOrId("pikachu")).thenReturn(new PokemonDetail(25, "pikachu", "pikachu", "pikachu.png",
                0.4, 6.0, null, List.of(), List.of(), null, false, new EvolutionNode("pikachu", List.of()), false, 20));

        mockMvc.perform(get(BASE_URL + "/pikachu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retryAfterSeconds").value(20))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "20"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void shouldOmitRetryAfterWhenThePageWasNotRateLimited() throws Exception {
        when(service.findPage(0, 20)).thenReturn(page(
                new PokemonItem(1, "bulbasaur", "img-1", "Seed Pokemon", 6.9, List.of())));

        mockMvc.perform(get(BASE_URL))
                .andExpect(jsonPath("$.retryAfterSeconds").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void shouldAnswerServiceUnavailableAtOnceWhileARateLimitPauseIsInForce() throws Exception {
        when(service.findByNameOrId("pikachu")).thenThrow(new PokeApiRateLimitException(
                java.time.Instant.now().plusSeconds(20), false, null));

        mockMvc.perform(get(BASE_URL + "/pikachu"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, org.hamcrest.Matchers.matchesPattern("19|20")))
                .andExpect(jsonPath("$.title").value("Upstream rate limit"));
    }

    @Test
    void shouldReturnInternalErrorWithoutLeakingDetails() throws Exception {
        when(service.findPage(0, 20)).thenThrow(new IllegalStateException("connection string with secrets"));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    static FeignException upstreamError(int status, Map<String, java.util.Collection<String>> headers) {
        Response response = Response.builder()
                .status(status)
                .reason("upstream")
                .request(upstreamRequest())
                .headers(headers)
                .build();
        return FeignException.errorStatus("PokemonClient#findAll", response);
    }

    static RetryableException retryable(int status, Long retryAfter) {
        return new RetryableException(status, "upstream", Request.HttpMethod.GET, retryAfter, upstreamRequest());
    }

    static Request upstreamRequest() {
        return Request.create(Request.HttpMethod.GET, "https://pokeapi.co/api/v2/pokemon", Map.of(),
                null, StandardCharsets.UTF_8, null);
    }
}
