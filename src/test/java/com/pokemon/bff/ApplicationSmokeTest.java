package com.pokemon.bff;

import com.pokemon.bff.client.PokemonClient;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Starts the whole application — every configuration class, the real Flyway migration (on H2 in MySQL mode), JPA,
 * the caches, Spring MVC — and runs a local record through its life over HTTP. Only PokeAPI is simulated.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smoke;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "pokemon.cache.warmup.enabled=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=WARN",
        "logging.level.org.hibernate.type.descriptor.sql.BasicBinder=WARN"
})
@AutoConfigureMockMvc
class ApplicationSmokeTest {
    private static final String LOCAL = "/api/v1/local-pokemons";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PokemonClient pokeApi;

    private static FeignException notInPokeApi() {
        var request = Request.create(Request.HttpMethod.GET, "https://pokeapi.co/api/v2/pokemon/x", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("PokemonClient#findByNameOrId",
                Response.builder().status(404).reason("Not Found").request(request).headers(Map.of()).build());
    }

    @Test
    void shouldStartAndServeTheDocumentationAndTheHealthCheck() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/local-pokemons/{id}'].patch").exists());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void shouldStartWithTheDemonstrationData() throws Exception {
        // A replicated PokeAPI Pokemon enriched with proprietary fields (V2__demo_data.sql)...
        mockMvc.perform(get(LOCAL + "/pikachu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Mouse Pokémon"))
                .andExpect(jsonPath("$.stats.length()").value(6))
                .andExpect(jsonPath("$.metadata.localizedName").value("ピカチュウ"))
                .andExpect(jsonPath("$.metadata.classificationTag").value("MASCOT"));
        // ...and a Pokemon created locally, which exists only here.
        mockMvc.perform(get(LOCAL + "/voltmouse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(90001))
                .andExpect(jsonPath("$.metadata.region").value("Demo Lab"));
        mockMvc.perform(get(LOCAL).param("size", "100"))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(10)));
    }

    @Test
    void shouldRunALocalRecordThroughItsLife() throws Exception {
        // Neither the name nor the id belongs to a PokeAPI Pokemon.
        when(pokeApi.findByNameOrId(anyString())).thenThrow(notInPokeApi());

        var created = mockMvc.perform(post(LOCAL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"id": 9101, "name": "smokemon", "image": "https://img.example/smokemon.png", "mass": 4.5,
                         "stats": [{"name": "hp", "value": 40}], "region": "Kanto"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, LOCAL + "/9101"))
                .andReturn().getResponse();
        String createdEtag = created.getHeader(HttpHeaders.ETAG);

        var read = mockMvc.perform(get(LOCAL + "/smokemon"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, createdEtag))
                .andExpect(jsonPath("$.metadata.region").value("Kanto"))
                .andReturn().getResponse();
        // One representation for the reads and the writes of a local record.
        assertEquals(read.getContentAsString(), created.getContentAsString());

        var updated = mockMvc.perform(patch(LOCAL + "/9101").header(HttpHeaders.IF_MATCH, createdEtag)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mass\": 5.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mass").value(5.0))
                .andExpect(jsonPath("$.metadata.region").value("Kanto"))
                .andReturn().getResponse();
        mockMvc.perform(patch(LOCAL + "/9101").header(HttpHeaders.IF_MATCH, createdEtag)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mass\": 6.0}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(header().string(HttpHeaders.ETAG, updated.getHeader(HttpHeaders.ETAG)));

        mockMvc.perform(delete(LOCAL + "/9101").header(HttpHeaders.IF_MATCH, updated.getHeader(HttpHeaders.ETAG)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(LOCAL + "/9101"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void shouldAnswerEveryBadRequestWithTheSameTitle() throws Exception {
        mockMvc.perform(patch(LOCAL + "/abc").contentType(MediaType.APPLICATION_JSON).content("{\"mass\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.field").value("id"));
        mockMvc.perform(patch(LOCAL + "/1").contentType(MediaType.APPLICATION_JSON).content("{\"heigth\": 1}"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.field").value("heigth"));
        mockMvc.perform(patch(LOCAL + "/1").contentType(MediaType.APPLICATION_JSON).content("{\"mass\": -1}"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.field").value("mass"));
    }
}
