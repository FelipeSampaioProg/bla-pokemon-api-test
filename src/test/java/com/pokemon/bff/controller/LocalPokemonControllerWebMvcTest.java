package com.pokemon.bff.controller;

import com.pokemon.bff.dto.EvolutionNode;
import com.pokemon.bff.dto.PokemonCreateRequest;
import com.pokemon.bff.dto.PokemonDetail;
import com.pokemon.bff.dto.PokemonItem;
import com.pokemon.bff.dto.PokemonMetadata;
import com.pokemon.bff.dto.PokemonSyncResult;
import com.pokemon.bff.dto.PokemonPage;
import com.pokemon.bff.dto.PokemonReplaceRequest;
import com.pokemon.bff.dto.PokemonStat;
import com.pokemon.bff.dto.PokemonUpdateRequest;
import com.pokemon.bff.dto.Skill;
import com.pokemon.bff.dto.Versioned;
import com.pokemon.bff.exception.InvalidFieldException;
import com.pokemon.bff.exception.PreconditionFailedException;
import com.pokemon.bff.service.LocalPokemonService;
import com.pokemon.bff.service.VersionCondition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests of the local Pokemon resource: CRUD verbs, Location, ETag / If-Match, the strict reading of the
 * write payloads and error mapping.
 */
@WebMvcTest(LocalPokemonController.class)
class LocalPokemonControllerWebMvcTest {
    private static final String BASE_URL = "/api/v1/local-pokemons";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocalPokemonService service;

    /** A local record as every read and write returns it (one representation, User Story 04). */
    private static PokemonDetail localmon(String image, Double mass, String category) {
        return new PokemonDetail(9001, "localmon", "localmon", image, 1.0, mass, category,
                List.of(new Skill("splash", "u-splash")), List.of(new PokemonStat("hp", 50)), "Created locally", true,
                new EvolutionNode("localmon", List.of()), true, null, new PokemonMetadata("Localmon", "Kanto", "CUSTOM"));
    }

    /** The record's version, and the ETag the API makes of it. */
    private static final String VERSION = "3-1700000000000";
    private static final String ETAG = "\"" + VERSION + "\"";
    private static final VersionCondition IF_MATCH_ETAG = VersionCondition.oneOf(List.of(VERSION));

    private static Versioned<PokemonDetail> versioned(PokemonDetail detail) {
        return new Versioned<>(detail, VERSION);
    }

    private static PokemonDetail localDetail() {
        return new PokemonDetail(9001, "localmon", "https://img.example/local.png", null, 12.5,
                List.of(new PokemonStat("hp", 50)), "Created locally", new EvolutionNode("localmon", List.of()));
    }

    // --- reads ---

    @Test
    void shouldListLocalRecordsAndAskClientsToRevalidate() throws Exception {
        when(service.findLocalPage(1, 5)).thenReturn(new PokemonPage(List.of(), 1, 5, 3, 1));

        mockMvc.perform(get(BASE_URL).param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(header().exists(HttpHeaders.ETAG));

        verify(service).findLocalPage(1, 5);
    }

    @Test
    void shouldReturnLocalDetailWithTheRecordVersionAsETag() throws Exception {
        when(service.findLocal("localmon")).thenReturn(new Versioned<>(localDetail(), VERSION));

        mockMvc.perform(get(BASE_URL + "/localmon"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(jsonPath("$.id").value(9001))
                .andExpect(jsonPath("$.height").doesNotExist())
                .andExpect(jsonPath("$.mass").value(12.5))
                .andExpect(jsonPath("$.stats[0].name").value("hp"))
                .andExpect(jsonPath("$.evolutionLineage.name").value("localmon"));
    }

    @Test
    void shouldAnswerNotModifiedWhenTheVersionIsUnchanged() throws Exception {
        when(service.findLocal("localmon")).thenReturn(new Versioned<>(localDetail(), VERSION));

        mockMvc.perform(get(BASE_URL + "/localmon").header(HttpHeaders.IF_NONE_MATCH, ETAG))
                .andExpect(status().isNotModified());
    }

    @Test
    void shouldReturnNotFoundWhenPokemonIsNotInLocalStore() throws Exception {
        when(service.findLocal("pikachu"))
                .thenThrow(new NoSuchElementException("Pokemon pikachu not found in the local store"));

        mockMvc.perform(get(BASE_URL + "/pikachu"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pokemon pikachu not found in the local store"));
    }

    // --- POST ---

    @Test
    void shouldCreateAndPointToTheNewRecord() throws Exception {
        when(service.create(any())).thenReturn(versioned(localmon("https://img.example/local.png", 12.5, "Custom Pokemon")));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":9001,\"name\":\"localmon\",\"stats\":[{\"name\":\"hp\",\"value\":50}]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, BASE_URL + "/9001"))
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(jsonPath("$.image").value("https://img.example/local.png"))
                .andExpect(jsonPath("$.mass").value(12.5))
                .andExpect(jsonPath("$.category").value("Custom Pokemon"))
                .andExpect(jsonPath("$.stats[0].name").value("hp"))
                .andExpect(jsonPath("$.skills[0].name").value("splash"))
                // Same representation as GET /local-pokemons/{id}: proprietary fields in "metadata".
                .andExpect(jsonPath("$.metadata.localizedName").value("Localmon"))
                .andExpect(jsonPath("$.localizedName").doesNotExist())
                .andExpect(jsonPath("$.evolutionLineage.name").value("localmon"))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.weight").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.locallyModified").doesNotExist())
                .andExpect(jsonPath("$.syncedAt").doesNotExist())
                .andExpect(jsonPath("$.stats[0].pokemon").doesNotExist());
    }

    @Test
    void shouldAcceptNewAndFormerFieldNamesOnCreate() throws Exception {
        var captor = ArgumentCaptor.forClass(PokemonCreateRequest.class);
        when(service.create(captor.capture())).thenReturn(versioned(localmon(null, null, null)));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":9001,\"name\":\"localmon\",\"image\":\"new.png\",\"mass\":12.5,\"category\":\"Custom\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":9002,\"name\":\"oldmon\",\"imageUrl\":\"old.png\",\"weight\":3.0}"))
                .andExpect(status().isCreated());

        var requests = captor.getAllValues();
        assertEquals("new.png", requests.get(0).image());
        assertEquals(12.5, requests.get(0).mass());
        assertEquals("Custom", requests.get(0).category());
        assertEquals("old.png", requests.get(1).image());
        assertEquals(3.0, requests.get(1).mass());
    }

    @Test
    void shouldReturnBadRequestWhenCreateBodyIsMissing() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request body is required"));
    }

    @Test
    void shouldRejectAnUnknownFieldOnCreate() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":9001,\"name\":\"localmon\",\"weigth\":3.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("weigth"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnConflictWhenRecordViolatesUniqueness() throws Exception {
        when(service.create(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":25,\"name\":\"pikachu\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    // --- PUT (replace) and PATCH (partial) ---

    @Test
    void shouldReplaceWithPutLeavingOutFieldsCleared() throws Exception {
        var captor = ArgumentCaptor.forClass(PokemonReplaceRequest.class);
        when(service.replace(eq(9001), isNull(), captor.capture())).thenReturn(versioned(localmon(null, null, null)));

        mockMvc.perform(put(BASE_URL + "/9001").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"localmon\",\"mass\":2.0}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, ETAG));

        var request = captor.getValue();
        assertEquals("localmon", request.name());
        assertEquals(2.0, request.mass());
        assertNull(request.image());
        assertEquals(List.of(), request.stats());
        verify(service, never()).update(any(), any(), any());
    }

    @Test
    void shouldUpdateOnlyTheFieldsSentWithPatch() throws Exception {
        var captor = ArgumentCaptor.forClass(PokemonUpdateRequest.class);
        when(service.update(eq(9001), isNull(), captor.capture()))
                .thenReturn(versioned(localmon("https://img.example/local.png", 12.5, "Custom")));

        mockMvc.perform(patch(BASE_URL + "/9001").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"Johto\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, ETAG));

        assertEquals("Johto", captor.getValue().region());
        assertNull(captor.getValue().name());
        assertNull(captor.getValue().stats());
        verify(service, never()).replace(any(), any(), any());
    }

    @Test
    void shouldAcceptTheIdInTheBodyAndLeaveTheCheckToTheService() throws Exception {
        var captor = ArgumentCaptor.forClass(PokemonUpdateRequest.class);
        when(service.update(eq(9001), isNull(), captor.capture())).thenReturn(versioned(localmon(null, null, null)));

        mockMvc.perform(patch(BASE_URL + "/9001").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":9001,\"region\":\"Johto\"}"))
                .andExpect(status().isOk());

        assertEquals(9001, captor.getValue().id());
    }

    @Test
    void shouldReturnNotFoundWhenPatchingOrReplacingAMissingRecord() throws Exception {
        when(service.update(eq(404), any(), any())).thenThrow(new NoSuchElementException("Pokemon not found with id 404"));
        when(service.replace(eq(404), any(), any())).thenThrow(new NoSuchElementException("Pokemon not found with id 404"));

        mockMvc.perform(patch(BASE_URL + "/404").contentType(MediaType.APPLICATION_JSON).content("{\"mass\":2.0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pokemon not found with id 404"));
        mockMvc.perform(put(BASE_URL + "/404").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ghost\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pokemon not found with id 404"));
    }

    @Test
    void shouldSayThatARecordWasDeletedSoClientsCanOfferToRestoreIt() throws Exception {
        when(service.update(eq(25), any(), any())).thenThrow(new com.pokemon.bff.exception.PokemonDeletedException(
                "Pokemon 25 was deleted from the local store"));

        mockMvc.perform(patch(BASE_URL + "/25").contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"Kanto\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.deleted").value(true));
        when(service.update(eq(404), any(), any())).thenThrow(new NoSuchElementException("Pokemon not found with id 404"));
        mockMvc.perform(patch(BASE_URL + "/404").contentType(MediaType.APPLICATION_JSON).content("{\"mass\":2.0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.deleted").doesNotExist());
    }

    @Test
    void shouldReturnBadRequestWhenReplaceHasNoName() throws Exception {
        when(service.replace(eq(9001), any(), any())).thenThrow(new InvalidFieldException("name", "Pokemon name must not be blank"));

        mockMvc.perform(put(BASE_URL + "/9001").contentType(MediaType.APPLICATION_JSON).content("{\"mass\":2.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Pokemon name must not be blank"))
                .andExpect(jsonPath("$.field").value("name"));
    }

    @Test
    void shouldReturnConflictWhenPokemonWasModifiedConcurrently() throws Exception {
        when(service.update(eq(25), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("PokemonEntity", 25));

        mockMvc.perform(patch(BASE_URL + "/25").contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"Mouse\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Concurrent modification"));
    }

    // --- If-Match (optimistic concurrency over HTTP) ---

    @Test
    void shouldPassIfMatchToTheWrites() throws Exception {
        when(service.update(eq(9001), eq(IF_MATCH_ETAG), any())).thenReturn(versioned(localmon(null, null, null)));
        when(service.replace(eq(9001), eq(IF_MATCH_ETAG), any())).thenReturn(versioned(localmon(null, null, null)));

        mockMvc.perform(patch(BASE_URL + "/9001").header(HttpHeaders.IF_MATCH, ETAG)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"Johto\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(BASE_URL + "/9001").header(HttpHeaders.IF_MATCH, ETAG)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"localmon\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete(BASE_URL + "/9001").header(HttpHeaders.IF_MATCH, ETAG))
                .andExpect(status().isNoContent());

        verify(service).delete(9001, IF_MATCH_ETAG);
    }

    @Test
    void shouldAnswerPreconditionFailedWithTheCurrentETagWhenTheRecordChanged() throws Exception {
        when(service.update(eq(9001), eq(VersionCondition.oneOf(List.of("1-1700000000000"))), any()))
                .thenThrow(new PreconditionFailedException("Pokemon 9001 was changed since it was read", VERSION));

        mockMvc.perform(patch(BASE_URL + "/9001").header(HttpHeaders.IF_MATCH, "\"1-1700000000000\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"Johto\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(jsonPath("$.title").value("Precondition failed"))
                .andExpect(jsonPath("$.detail").value("Pokemon 9001 was changed since it was read"));
    }

    // --- malformed payloads: 400 naming the problem, before the service is called ---

    private void expectBadPatch(String body, String field, String detail) throws Exception {
        var result = mockMvc.perform(patch(BASE_URL + "/9001").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.detail").value(detail));
        if (field == null) {
            result.andExpect(jsonPath("$.field").doesNotExist());
        } else {
            result.andExpect(jsonPath("$.field").value(field));
        }
    }

    @Test
    void shouldRejectUnknownFieldsInsteadOfIgnoringThem() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/9001").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"localmon\",\"heigth\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("heigth"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith(
                        "Unknown field 'heigth'; the accepted fields are ")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("height")));
        mockMvc.perform(put(BASE_URL + "/9001").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"localmon\",\"stats\":[{\"name\":\"hp\",\"value\":5,\"effort\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("stats[0].effort"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectValuesOfTheWrongTypeInsteadOfConvertingThem() throws Exception {
        expectBadPatch("{\"stats\":[{\"name\":\"hp\",\"value\":1.7}]}", "stats[0].value",
                "Missing or invalid value for 'stats[0].value': expected an integer");
        expectBadPatch("{\"name\":123}", "name", "Missing or invalid value for 'name': expected a text");
        expectBadPatch("{\"height\":\"5\"}", "height", "Missing or invalid value for 'height': expected a number");
        expectBadPatch("{\"height\":true}", "height", "Missing or invalid value for 'height': expected a number");
        expectBadPatch("{\"stats\":{\"name\":\"hp\"}}", "stats", "Missing or invalid value for 'stats': expected a list");

        verifyNoInteractions(service);
    }

    @Test
    void shouldNameTheFieldWhenARequiredValueIsMissing() throws Exception {
        expectBadPatch("{\"stats\":[{\"name\":\"hp\"}]}", "stats[0].value",
                "Missing or invalid value for 'stats[0].value': expected an integer");
        expectBadPatch("{\"stats\":[{\"name\":\"hp\",\"value\":null}]}", "stats[0].value",
                "Missing or invalid value for 'stats[0].value': expected an integer");

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectMalformedJsonDuplicateFieldsAndNonObjectBodies() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/9001").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Malformed JSON: ")));
        mockMvc.perform(patch(BASE_URL + "/9001").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"name\":\"b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Duplicate")));
        expectBadPatch("[]", null, "The request body must be a JSON object");

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectAnInvalidIdInThePath() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/abc").contentType(MediaType.APPLICATION_JSON).content("{\"mass\":2.0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE_URL + "/99999999999").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete(BASE_URL + "/abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectAWrongContentType() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/9001").contentType(MediaType.TEXT_PLAIN).content("{\"mass\":2.0}"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(service);
    }

    // --- replication (User Story 03) ---

    @Test
    void shouldReplicateOnDemandAndPointToTheNewRecord() throws Exception {
        when(service.synchronize("pikachu")).thenReturn(new Versioned<>(new PokemonSyncResult(
                PokemonSyncResult.Outcome.CREATED, new PokemonDetail(25, "pikachu", "pikachu", "pikachu.png", 0.4,
                6.0, "Mouse Pokemon", List.of(), List.of(), "Electric mouse.", true,
                new EvolutionNode("pikachu", List.of()), true)), VERSION));

        mockMvc.perform(post(BASE_URL + "/pikachu/sync"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, BASE_URL + "/25"))
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(jsonPath("$.outcome").value("CREATED"))
                .andExpect(jsonPath("$.pokemon.id").value(25))
                .andExpect(jsonPath("$.pokemon.category").value("Mouse Pokemon"));
    }

    @Test
    void shouldAnswerOkWhenTheReplicaWasRefreshedOrKept() throws Exception {
        when(service.synchronize("25")).thenReturn(new Versioned<>(new PokemonSyncResult(
                PokemonSyncResult.Outcome.KEPT_LOCAL_CHANGES, localmon("custom.png", 6.0, null)), VERSION));

        mockMvc.perform(post(BASE_URL + "/25/sync"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.outcome").value("KEPT_LOCAL_CHANGES"));
    }

    @Test
    void shouldShowProprietaryFieldsInLocalReads() throws Exception {
        when(service.findLocal("pikachu")).thenReturn(new Versioned<>(new PokemonDetail(25, "pikachu", "pikachu",
                "pikachu.png", 0.4, 6.0, "Mouse Pokemon", List.of(), List.of(), "Electric mouse.", true,
                new EvolutionNode("pikachu", List.of()), true, null, new PokemonMetadata("Pikachu (ES)", "Kanto", "STARTER")),
                VERSION));
        when(service.findLocalPage(0, 20)).thenReturn(new PokemonPage(List.of(
                new PokemonItem(25, "pikachu", "pikachu.png", "Mouse Pokemon", 6.0, List.of(), true,
                        new PokemonMetadata(null, "Kanto", null)),
                new PokemonItem(9001, "localmon", null, null, null, List.of())), 0, 20, 2, 1));

        mockMvc.perform(get(BASE_URL + "/pikachu"))
                .andExpect(jsonPath("$.metadata.localizedName").value("Pikachu (ES)"))
                .andExpect(jsonPath("$.metadata.region").value("Kanto"))
                .andExpect(jsonPath("$.metadata.classificationTag").value("STARTER"));
        mockMvc.perform(get(BASE_URL))
                .andExpect(jsonPath("$.content[0].metadata.region").value("Kanto"))
                .andExpect(jsonPath("$.content[1].metadata").doesNotExist());
    }

    // --- DELETE ---

    @Test
    void shouldReturnNoContentWhenDeletingLocalPokemon() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/25"))
                .andExpect(status().isNoContent());

        verify(service).delete(25, null);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingLocalPokemon() throws Exception {
        doThrow(new NoSuchElementException("Pokemon not found with id 999")).when(service).delete(999, null);

        mockMvc.perform(delete(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pokemon not found with id 999"));
    }
}
