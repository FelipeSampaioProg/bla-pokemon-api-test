package com.pokemon.bff.service;

import com.pokemon.bff.client.PokeApiRateLimitException;
import com.pokemon.bff.client.PokeApiRetrier;
import com.pokemon.bff.client.PokeApiStatus;
import com.pokemon.bff.client.PokemonClient;
import com.pokemon.bff.client.dto.EvolutionChainResponse;
import com.pokemon.bff.client.dto.PokemonDetailsResponse;
import com.pokemon.bff.client.dto.PokemonListResponse;
import com.pokemon.bff.client.dto.PokemonSpeciesResponse;
import com.pokemon.bff.config.PokemonCacheNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Cached access to the PokeAPI resources, one cache per resource type.
 * <p>
 * The list entry (User Story 01) and the detail view (User Story 02) are both assembled from these resources, so
 * opening the detail of a card reuses the calls the card already made, and both always show the same data.
 * Failures are never cached: the next request tries again. {@code sync = true} lets only one request load a
 * missing entry while concurrent requests for it wait.
 */
@Service
public class PokeApiCache {
    private final PokemonClient client;
    private final PokeApiStatus status;
    private final PokeApiRetrier retrier;

    @Autowired
    public PokeApiCache(PokemonClient client, PokeApiStatus status, PokeApiRetrier retrier) {
        this.client = client;
        this.status = status;
        this.retrier = retrier;
    }

    /** No health tracking, no retries: for unit tests. */
    public PokeApiCache(PokemonClient client) {
        this(client, new PokeApiStatus(), PokeApiRetrier.none());
    }

    @Cacheable(cacheNames = PokemonCacheNames.POKEMON_PAGE,
            key = "T(com.pokemon.bff.service.PokemonCacheKeys).references(#offset, #limit)", sync = true)
    public PokemonListResponse references(int offset, int limit) {
        // The reference list answers the page request itself: worth a retry.
        var list = call(() -> client.findAll(offset, limit), true);
        if (list == null) {
            throw new IllegalStateException("PokeAPI returned an empty Pokemon list response");
        }
        return list;
    }

    /**
     * @param retry {@code true} when the call answers a request directly (detail view); {@code false} for the entries
     *              of a page, which degrade instead. Not part of the cache key: the resource is the same either way.
     */
    @Cacheable(cacheNames = PokemonCacheNames.POKEAPI_POKEMON,
            key = "T(com.pokemon.bff.service.PokemonCacheKeys).pokemon(#nameOrId)", sync = true)
    public PokemonDetailsResponse pokemon(String nameOrId, boolean retry) {
        return call(() -> client.findByNameOrId(PokemonCacheKeys.pokemon(nameOrId)), retry);
    }

    /** @param retry see {@link #pokemon(String, boolean)} */
    @Cacheable(cacheNames = PokemonCacheNames.POKEAPI_SPECIES,
            key = "T(com.pokemon.bff.service.PokemonCacheKeys).pokemon(#nameOrId)", sync = true)
    public PokemonSpeciesResponse species(String nameOrId, boolean retry) {
        return call(() -> client.findSpeciesByNameOrId(PokemonCacheKeys.pokemon(nameOrId)), retry);
    }

    @Cacheable(cacheNames = PokemonCacheNames.POKEAPI_EVOLUTION, key = "#id", sync = true)
    public EvolutionChainResponse evolutionChain(int id) {
        // Only the detail view loads evolution chains.
        return call(() -> client.findEvolutionChainById(id), true);
    }

    /** Runs the PokeAPI calls of one request (e.g. the detail view) under a single retry time budget. */
    public <T> T withinRequestBudget(Supplier<T> work) {
        return retrier.withinBudget(work);
    }

    /** Runs a PokeAPI call (a cache miss) under the retry policy and records its outcome for the health check. */
    private <T> T call(Supplier<T> request, boolean retry) {
        try {
            T result = retrier.execute(request, retry);
            status.recordSuccess();
            return result;
        } catch (PokeApiRateLimitException e) {
            if (e.upstreamCalled()) {
                status.recordRateLimit(e.retryAfter());
            }
            throw e;
        } catch (RuntimeException e) {
            status.record(e);
            throw e;
        }
    }
}
