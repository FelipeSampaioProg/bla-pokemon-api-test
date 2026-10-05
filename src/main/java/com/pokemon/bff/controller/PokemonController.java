package com.pokemon.bff.controller;

import com.pokemon.bff.dto.PokemonDetail;
import com.pokemon.bff.dto.PokemonItem;
import com.pokemon.bff.dto.PokemonPage;
import com.pokemon.bff.service.PokeApiPokemonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Pokemon as published by PokeAPI: read-only. Records created or edited locally live in
 * {@link LocalPokemonController} ({@code /api/v1/local-pokemons}), so each URI names exactly one resource.
 * <p>
 * Validation and domain rules live in {@link PokeApiPokemonService}; failures are turned into
 * {@code application/problem+json} responses by {@code ApiExceptionHandler}.
 */
@RestController
@RequestMapping(PokemonController.BASE_PATH)
@Tag(name = "PokeAPI Pokemon", description = "Read-only Pokemon data from PokeAPI")
public class PokemonController {
    public static final String BASE_PATH = "/api/v1/pokemons";

    /** For a detail that carries proprietary fields: they change through the local resource. */
    private static final CacheControl REVALIDATE = CacheControl.noCache();

    private final PokeApiPokemonService service;
    private final CacheControl completeResponse;

    /**
     * @param maxAge {@code 0} (the default): complete responses are sent with {@code no-cache}, so clients revalidate
     *               every time (a cheap 304 through the ETag) and a list card never shows older data than its
     *               detail. A positive value lets clients reuse both for that long ({@code max-age}, public).
     */
    public PokemonController(PokeApiPokemonService service, @Value("${pokemon.http.max-age:0s}") Duration maxAge) {
        this.service = service;
        this.completeResponse = maxAge.isZero() || maxAge.isNegative()
                ? CacheControl.noCache()
                : CacheControl.maxAge(maxAge).cachePublic();
    }

    @Operation(summary = "List Pokemon from PokeAPI",
            description = "Returns a paginated page of Pokemon with image, category, mass and skills. Entries that "
                    + "could not be loaded from PokeAPI are returned with available=false, and entries whose species "
                    + "could not be loaded keep their data with categoryAvailable=false; such a page is sent with "
                    + "Cache-Control: no-store so it is never reused. When entries are missing because PokeAPI is "
                    + "rate limiting us, the page also carries retryAfterSeconds and the Retry-After header.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of Pokemon"),
            @ApiResponse(responseCode = "304", description = "Not modified (If-None-Match matched the ETag)", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid page or size", content = @Content),
            @ApiResponse(responseCode = "502", description = "PokeAPI request failed", content = @Content),
            @ApiResponse(responseCode = "503", description = "PokeAPI rate limit reached; see Retry-After", content = @Content),
            @ApiResponse(responseCode = "504", description = "PokeAPI did not respond in time", content = @Content)
    })
    @GetMapping
    public ResponseEntity<PokemonPage> findAll(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, between 1 and 100") @RequestParam(defaultValue = "20") int size) {
        var result = service.findPage(page, size);
        boolean partial = result.content().stream().anyMatch(PokemonItem::partial);
        var response = ResponseEntity.ok().cacheControl(cacheControl(partial));
        if (result.retryAfterSeconds() != null) {
            // Entries are missing because PokeAPI is rate limiting us: tell the client when reloading helps.
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
        }
        return response.body(result);
    }

    @Operation(summary = "Get a Pokemon from PokeAPI by name or id",
            description = "Returns the full detail: image, category, height, mass, skills, base stats, description "
                    + "and evolution lineage. A detail with a part that could not be loaded "
                    + "(descriptionAvailable/evolutionAvailable false) is sent with Cache-Control: no-store. A detail "
                    + "with proprietary fields (metadata) is always sent with no-cache, even when a max-age is set, "
                    + "because those fields change through the local resource.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pokemon detail"),
            @ApiResponse(responseCode = "304", description = "Not modified (If-None-Match matched the ETag)", content = @Content),
            @ApiResponse(responseCode = "400", description = "Blank identifier", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pokemon not found in PokeAPI", content = @Content),
            @ApiResponse(responseCode = "502", description = "PokeAPI request failed", content = @Content),
            @ApiResponse(responseCode = "503", description = "PokeAPI rate limit reached; see Retry-After", content = @Content),
            @ApiResponse(responseCode = "504", description = "PokeAPI did not respond in time", content = @Content)
    })
    @GetMapping("/{pokemon}")
    public ResponseEntity<PokemonDetail> findByNameOrId(@PathVariable String pokemon) {
        var detail = service.findByNameOrId(pokemon);
        var response = ResponseEntity.ok().cacheControl(detail.metadata() != null && !detail.partial()
                // The proprietary fields merged from the local store change through this API: never let a browser
                // or proxy reuse them for max-age; revalidating costs a 304.
                ? REVALIDATE
                : cacheControl(detail.partial()));
        if (detail.retryAfterSeconds() != null) {
            // Description or lineage are missing because PokeAPI is rate limiting us, as on a page.
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(detail.retryAfterSeconds()));
        }
        return response.body(detail);
    }

    /** Complete PokeAPI data may be reused by browsers and proxies; a partial answer must be fetched again. */
    private CacheControl cacheControl(boolean partial) {
        return partial ? CacheControl.noStore() : completeResponse;
    }
}
