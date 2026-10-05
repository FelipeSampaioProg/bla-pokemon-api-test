package com.pokemon.bff.controller;

import com.pokemon.bff.dto.PokemonCreateRequest;
import com.pokemon.bff.dto.PokemonDetail;
import com.pokemon.bff.dto.PokemonPage;
import com.pokemon.bff.dto.PokemonReplaceRequest;
import com.pokemon.bff.dto.PokemonSyncResult;
import com.pokemon.bff.dto.PokemonUpdateRequest;
import com.pokemon.bff.dto.Versioned;
import com.pokemon.bff.service.LocalPokemonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Pokemon stored in the local database: records created through this API plus the copies synchronised from
 * PokeAPI reads. Full CRUD; {@code PUT} replaces a record and {@code PATCH} changes only the fields sent.
 * <p>
 * A write that changes PokeAPI data takes ownership of the record: it is marked {@code locally_modified} and the
 * PokeAPI synchronisation never updates it again (a {@code PATCH} of only proprietary fields does not). On a
 * synchronised copy, a {@code PUT} that leaves fields out therefore clears them for good (stats, skills,
 * description...); use {@code PATCH} to change a few fields and keep the rest.
 * <p>
 * Optimistic concurrency: the detail and every write answer with the record's {@code ETag}. Sending it back in
 * {@code If-Match} on {@code PUT}, {@code PATCH} or {@code DELETE} makes the write fail with 412 if someone changed
 * the record in between, instead of silently overwriting that change. {@code If-Match} is optional.
 * <p>
 * Responses are sent with {@code Cache-Control: no-cache}: clients may keep them but must revalidate (the ETag makes
 * that a cheap 304), because the records change through this same API.
 */
@RestController
@RequestMapping(LocalPokemonController.BASE_PATH)
@Tag(name = "Local Pokemon", description = "Pokemon stored in the local database (CRUD)")
public class LocalPokemonController {
    public static final String BASE_PATH = "/api/v1/local-pokemons";
    private static final CacheControl REVALIDATE = CacheControl.noCache();

    private final LocalPokemonService service;

    public LocalPokemonController(LocalPokemonService service) {
        this.service = service;
    }

    @Operation(summary = "List local Pokemon", description = "Paginated list of the local records, ordered by id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of local Pokemon"),
            @ApiResponse(responseCode = "400", description = "Invalid page or size", content = @Content)
    })
    @GetMapping
    public ResponseEntity<PokemonPage> findAll(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, between 1 and 100") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(REVALIDATE).body(service.findLocalPage(page, size));
    }

    @Operation(summary = "Get a local Pokemon by name or id",
            description = "Local records have no evolution data, so the lineage contains only the Pokemon itself.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pokemon detail; the ETag header is the record's version"),
            @ApiResponse(responseCode = "400", description = "Blank or out-of-range identifier", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pokemon not found in the local store", content = @Content)
    })
    @GetMapping("/{pokemon}")
    public ResponseEntity<PokemonDetail> findByNameOrId(@PathVariable String pokemon) {
        var detail = service.findLocal(pokemon);
        return ResponseEntity.ok().cacheControl(REVALIDATE).eTag(EntityTags.format(detail.version())).body(detail.body());
    }

    @Operation(summary = "Create a local Pokemon",
            description = "Creates a record in the local database. The response carries the new record's URI in "
                    + "the Location header. The name may not belong to another Pokemon in PokeAPI (its replicated copy "
                    + "would collide with it), so PokeAPI is asked; if it cannot answer, the creation fails with the "
                    + "upstream error. To add proprietary fields to a PokeAPI Pokemon, use /{nameOrId}/sync and PATCH "
                    + "instead. The id may not belong to a PokeAPI Pokemon either (the record would take its place); "
                    + "PokeAPI is asked for both. Undoes an earlier DELETE of the same id.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pokemon created; Location points to it"),
            @ApiResponse(responseCode = "400", description = "Malformed payload, unknown field, invalid values, duplicate id or name, or an id or name of a PokeAPI Pokemon", content = @Content),
            @ApiResponse(responseCode = "409", description = "Record created concurrently with the same id or name", content = @Content)
    })
    @PostMapping
    public ResponseEntity<PokemonDetail> createPokemon(@RequestBody(required = false) PokemonCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        var created = service.create(request);
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + created.body().id()))
                .eTag(EntityTags.format(created.version())).body(created.body());
    }

    @Operation(summary = "Replace a local Pokemon",
            description = "Sets the complete state of an existing record: a field left out is cleared. "
                    + "name is required. Use PATCH to change only some fields. WARNING: the record then belongs to "
                    + "this API and the PokeAPI synchronisation stops updating it, so on a synchronised copy the "
                    + "PokeAPI data left out of the body (stats, skills, description...) is lost for good.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pokemon replaced; ETag is the new version"),
            @ApiResponse(responseCode = "400", description = "Malformed payload, unknown field or invalid values", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pokemon not found in the local store", content = @Content),
            @ApiResponse(responseCode = "409", description = "Pokemon modified concurrently or name already in use", content = @Content),
            @ApiResponse(responseCode = "412", description = "If-Match does not match the record's current ETag", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<PokemonDetail> replacePokemon(@PathVariable Integer id,
            @Parameter(description = "ETag read before; the write fails with 412 if the record changed since")
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) PokemonReplaceRequest request) {
        return versioned(service.replace(id, EntityTags.parseIfMatch(ifMatch), request));
    }

    @Operation(summary = "Replicate a Pokemon from PokeAPI into the local store now",
            description = "Copies the Pokemon's current PokeAPI data into the local store, so proprietary fields "
                    + "can be added to it. Proprietary fields already set are kept. A record holding local changes to "
                    + "PokeAPI fields is not overwritten (outcome KEPT_LOCAL_CHANGES). Undoes an earlier DELETE.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Replicated for the first time; Location points to it; ETag is its version"),
            @ApiResponse(responseCode = "200", description = "Local copy refreshed (or already current), or local changes kept; ETag is its version"),
            @ApiResponse(responseCode = "404", description = "Pokemon not found in PokeAPI", content = @Content),
            @ApiResponse(responseCode = "502", description = "PokeAPI request failed", content = @Content),
            @ApiResponse(responseCode = "503", description = "PokeAPI rate limit reached; see Retry-After", content = @Content),
            @ApiResponse(responseCode = "504", description = "PokeAPI did not respond in time", content = @Content)
    })
    @PostMapping("/{pokemon}/sync")
    public ResponseEntity<PokemonSyncResult> synchronize(@PathVariable String pokemon) {
        var synced = service.synchronize(pokemon);
        var result = synced.body();
        if (result.outcome() == PokemonSyncResult.Outcome.CREATED) {
            return ResponseEntity.created(URI.create(BASE_PATH + "/" + result.pokemon().id()))
                    .eTag(EntityTags.format(synced.version())).body(result);
        }
        return ResponseEntity.ok().eTag(EntityTags.format(synced.version())).body(result);
    }

    @Operation(summary = "Update some fields of a local Pokemon",
            description = "Changes only the fields sent; the others keep their value. A blank category clears it. "
                    + "Changing only proprietary fields (localizedName, region, classificationTag) keeps the record "
                    + "replicated from PokeAPI, and replicates it first if it is not in the local store yet. "
                    + "Changing PokeAPI data takes the record over: the synchronisation stops updating it. A record "
                    + "deleted through the API is not brought back by an update (404): use POST or /sync.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pokemon updated; ETag is the new version"),
            @ApiResponse(responseCode = "400", description = "Malformed payload, unknown field or invalid values", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pokemon not found in the local store (nor in PokeAPI, for a proprietary-fields-only update), or deleted through the API", content = @Content),
            @ApiResponse(responseCode = "409", description = "Pokemon modified concurrently or name already in use", content = @Content),
            @ApiResponse(responseCode = "412", description = "If-Match does not match the record's current ETag", content = @Content)
    })
    @PatchMapping("/{id}")
    public ResponseEntity<PokemonDetail> updatePokemon(@PathVariable Integer id,
            @Parameter(description = "ETag read before; the write fails with 412 if the record changed since")
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) PokemonUpdateRequest request) {
        return versioned(service.update(id, EntityTags.parseIfMatch(ifMatch), request));
    }

    @Operation(summary = "Delete a local Pokemon",
            description = "Deletes a record from the local database only. The deletion is remembered: reading the "
                    + "Pokemon from PokeAPI afterwards does not replicate it back. POST or /{nameOrId}/sync undo it.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pokemon deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid id", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pokemon not found in the local store", content = @Content),
            @ApiResponse(responseCode = "412", description = "If-Match does not match the record's current ETag", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePokemon(@PathVariable Integer id,
            @Parameter(description = "ETag read before; the deletion fails with 412 if the record changed since")
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        service.delete(id, EntityTags.parseIfMatch(ifMatch));
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<T> versioned(Versioned<T> result) {
        return ResponseEntity.ok().eTag(EntityTags.format(result.version())).body(result.body());
    }
}
