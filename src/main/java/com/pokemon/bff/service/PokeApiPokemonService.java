package com.pokemon.bff.service;

import com.pokemon.bff.client.PokeApiNotFoundException;
import com.pokemon.bff.client.PokeApiRateLimitException;
import com.pokemon.bff.client.dto.EvolutionChainResponse;
import com.pokemon.bff.client.dto.PokemonDetailsResponse;
import com.pokemon.bff.client.dto.PokemonReference;
import com.pokemon.bff.client.dto.PokemonSpeciesResponse;
import com.pokemon.bff.config.PokemonFetchConfiguration;
import com.pokemon.bff.dto.*;
import com.pokemon.bff.persistence.repository.PokemonRepository;
import com.pokemon.bff.sync.PokemonSnapshot;
import com.pokemon.bff.sync.PokemonSyncService;
import com.pokemon.bff.sync.PokemonSyncThrottle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pokemon as PokeAPI publishes them (resource {@code /api/v1/pokemons}): the paginated enumeration and the detailed
 * view, assembled from the cached PokeAPI resources. Parts that fail to load degrade the answer instead of failing it,
 * and what is read is replicated into the local store in the background. Local records are
 * {@link LocalPokemonService}'s business.
 */
@Service
public class PokeApiPokemonService {
    private static final Logger log = LoggerFactory.getLogger(PokeApiPokemonService.class);

    private final PokeApiCache pokeApi;
    private final PokemonSyncService syncService;
    private final PokemonSyncThrottle syncThrottle;
    private final PokemonRepository pokemonRepository;
    private final PokemonCatalogService catalogService;
    private final Executor fetchExecutor;

    public PokeApiPokemonService(PokeApiCache pokeApi, PokemonSyncService syncService, PokemonSyncThrottle syncThrottle,
            PokemonRepository pokemonRepository, PokemonCatalogService catalogService,
            @Qualifier(PokemonFetchConfiguration.POKEMON_FETCH_EXECUTOR) Executor fetchExecutor) {
        this.pokeApi = pokeApi;
        this.syncService = syncService;
        this.syncThrottle = syncThrottle;
        this.pokemonRepository = pokemonRepository;
        this.catalogService = catalogService;
        this.fetchExecutor = fetchExecutor;
    }

    /**
     * Pokemon enumeration backed by the PokeAPI. Entries are loaded in parallel from the cached PokeAPI resources;
     * an entry that cannot be loaded is returned as unavailable instead of failing the whole page.
     */
    public PokemonPage findPage(int page, int size) {
        int offset = Pagination.offset(page, size);
        var list = catalogService.fetchReferences(offset, size);
        var references = list.results() == null ? List.<PokemonReference>of() : list.results();
        var rateLimitedUntil = new AtomicReference<Instant>();
        var futures = references.stream()
                .map(reference -> CompletableFuture.supplyAsync(() -> loadItem(reference, rateLimitedUntil), fetchExecutor))
                .toList();
        var items = futures.stream().map(CompletableFuture::join).toList();
        return new PokemonPage(items, page, size, list.count(), totalPages(list.count(), size),
                retryAfterSeconds(rateLimitedUntil.get()));
    }

    public PokemonDetail findByNameOrId(String pokemon) {
        String normalized = PokemonCacheKeys.pokemon(pokemon);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Pokemon identifier must not be blank");
        }
        var detail = fetchDetailFromApi(normalized);
        // Same rule as the list entries: a Pokemon is written to the local store at most once per cache TTL.
        if (syncThrottle.tryAcquire(detail.id())) {
            try {
                syncService.sync(PokemonSnapshot.from(detail));
            } catch (RuntimeException e) {
                log.warn("Could not synchronise Pokemon {} to the local store: {}", detail.name(), e.getMessage());
            }
        }
        return detail.withMetadata(localMetadata(detail.id()));
    }

    /**
     * Proprietary fields stored locally for a PokeAPI Pokemon. The local store is optional for PokeAPI reads (as in
     * User Story 01): if it cannot be reached the detail is returned without them.
     */
    private PokemonMetadata localMetadata(int pokemonId) {
        try {
            return pokemonRepository.findById(pokemonId)
                    .map(entity -> PokemonMetadata.from(entity.getMetadata()))
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("Could not read the local fields of Pokemon {}: {}", pokemonId, e.getMessage());
            return null;
        }
    }

    /**
     * Full detail assembled from the cached PokeAPI resources, the same ones the list entries use. Only successful
     * resources are cached, so a detail with a part that failed to load is retried on the next request.
     */
    PokemonDetail fetchDetailFromApi(String pokemon) {
        // The detail makes up to four PokeAPI calls: they share one retry budget instead of one each.
        return pokeApi.withinRequestBudget(() -> assembleDetail(pokemon));
    }

    private PokemonDetail assembleDetail(String pokemon) {
        var rateLimitedUntil = new AtomicReference<Instant>();
        var resolved = resolvePokemon(pokemon);
        var details = resolved.details();
        // Alternate forms (charizard-mega-x) only resolve through their species name (charizard).
        var species = resolved.species() != null ? resolved.species() : loadSpecies(details, rateLimitedUntil);

        var lineage = species == null ? null : loadLineage(species, details.name(), rateLimitedUntil);

        return new PokemonDetail(details.id(), details.name(), details.speciesKey(), PokeApiMapper.image(details),
                PokeApiMapper.height(details), PokeApiMapper.mass(details), PokeApiMapper.category(species),
                PokeApiMapper.skills(details), PokeApiMapper.stats(details), PokeApiMapper.description(species),
                species != null,
                lineage != null ? lineage : new EvolutionNode(details.speciesKey(), List.of()), lineage != null,
                retryAfterSeconds(rateLimitedUntil.get()));
    }

    /** Keeps the latest moment a rate limit lifts, so a degraded answer can tell the client when to ask again. */
    private static void rememberRateLimit(AtomicReference<Instant> rateLimitedUntil, PokeApiRateLimitException e) {
        rateLimitedUntil.accumulateAndGet(e.retryAfter(),
                (current, next) -> current == null || next.isAfter(current) ? next : current);
    }

    private record ResolvedPokemon(PokemonDetailsResponse details, PokemonSpeciesResponse species) {
    }

    /**
     * Finds the Pokemon for an identifier. Evolution lineages and users name species, and some species have no
     * Pokemon of the same name ({@code lycanroc} only exists as {@code lycanroc-midday}, {@code -midnight}, ...):
     * on a 404 the identifier is tried as a species and its default variety is returned, together with the
     * species already loaded so it is not requested twice.
     */
    private ResolvedPokemon resolvePokemon(String pokemon) {
        try {
            return new ResolvedPokemon(pokeApi.pokemon(pokemon, true), null);
        } catch (PokeApiNotFoundException notFound) {
            PokemonSpeciesResponse species;
            try {
                species = pokeApi.species(pokemon, true);
            } catch (PokeApiNotFoundException speciesNotFound) {
                throw notFound;
            }
            String defaultPokemon = Optional.ofNullable(species).flatMap(PokemonSpeciesResponse::defaultPokemonName)
                    .orElseThrow(() -> notFound);
            return new ResolvedPokemon(pokeApi.pokemon(defaultPokemon, true), species);
        }
    }

    /**
     * Loads the species (description and evolution chain reference), or returns {@code null} when it cannot be
     * loaded: the image and stats already loaded are still worth returning.
     */
    private PokemonSpeciesResponse loadSpecies(PokemonDetailsResponse details,
            AtomicReference<Instant> rateLimitedUntil) {
        try {
            return pokeApi.species(details.speciesKey(), true);
        } catch (PokeApiRateLimitException e) {
            rememberRateLimit(rateLimitedUntil, e);
            return null;
        } catch (RuntimeException e) {
            log.warn("Could not load the species of Pokemon {}: {}", details.name(), e.getMessage());
            return null;
        }
    }

    /**
     * Loads the evolution lineage, or returns {@code null} when it cannot be loaded (upstream failure or an invalid
     * chain reference). The evolution chain is a separate PokeAPI call: losing it must not discard the image,
     * stats and description that were already loaded.
     */
    private EvolutionNode loadLineage(PokemonSpeciesResponse species, String pokemonName,
            AtomicReference<Instant> rateLimitedUntil) {
        try {
            var evolution = pokeApi.evolutionChain(extractEvolutionChainId(species));
            if (evolution == null || evolution.chain() == null) {
                return new EvolutionNode(pokemonName, List.of());
            }
            return mapEvolutionChain(evolution.chain());
        } catch (PokeApiRateLimitException e) {
            rememberRateLimit(rateLimitedUntil, e);
            return null;
        } catch (RuntimeException e) {
            log.warn("Could not load the evolution chain of Pokemon {}: {}", pokemonName, e.getMessage());
            return null;
        }
    }

    /**
     * Loads one entry; a failure degrades it to unavailable. When the failure is PokeAPI's rate limit, the latest
     * moment it lifts is kept so the page can tell the client when to reload.
     */
    private PokemonItem loadItem(PokemonReference reference, AtomicReference<Instant> rateLimitedUntil) {
        try {
            // A rate limit on the species only drops the category; the page still says when reloading helps.
            return catalogService.fetchItem(reference.name(), e -> rememberRateLimit(rateLimitedUntil, e));
        } catch (PokeApiRateLimitException e) {
            rememberRateLimit(rateLimitedUntil, e);
            return PokemonItem.unavailable(extractIdFromUrl(reference.url()), reference.name());
        } catch (RuntimeException e) {
            log.warn("Could not load Pokemon {} from PokeAPI: {}", reference.name(), e.getMessage());
            return PokemonItem.unavailable(extractIdFromUrl(reference.url()), reference.name());
        }
    }

    /** Seconds until the rate limit lifts, at least 1; {@code null} when the page was not rate limited. */
    private static Integer retryAfterSeconds(Instant until) {
        if (until == null) {
            return null;
        }
        long millis = Duration.between(Instant.now(), until).toMillis();
        return (int) Math.max(1, (millis + 999) / 1000);
    }

    private static int totalPages(int count, int size) {
        return (int) (((long) count + size - 1) / size);
    }

    private static int extractIdFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return 0;
        }
        String normalizedUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        try {
            return Integer.parseInt(normalizedUrl.substring(normalizedUrl.lastIndexOf('/') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private EvolutionNode mapEvolutionChain(EvolutionChainResponse.ChainLink link) {
        var name = link.species() == null ? null : link.species().name();
        var evolvesTo = link.evolvesTo() == null ? List.<EvolutionNode>of() : link.evolvesTo().stream()
                .map(this::mapEvolutionChain)
                .toList();
        return new EvolutionNode(name, evolvesTo);
    }

    private int extractEvolutionChainId(PokemonSpeciesResponse species) {
        if (species.evolutionChain() == null || species.evolutionChain().url() == null
                || species.evolutionChain().url().isBlank()) {
            throw new IllegalStateException("Evolution chain URL is missing");
        }
        String url = species.evolutionChain().url();
        String normalizedUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        int slashIndex = normalizedUrl.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == normalizedUrl.length() - 1) {
            throw new IllegalStateException("Invalid evolution chain URL: " + url);
        }
        try {
            return Integer.parseInt(normalizedUrl.substring(slashIndex + 1));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid evolution chain URL: " + url, e);
        }
}
}
