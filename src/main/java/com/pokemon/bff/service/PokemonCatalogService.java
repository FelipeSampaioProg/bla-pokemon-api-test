package com.pokemon.bff.service;

import com.pokemon.bff.client.PokeApiRateLimitException;
import com.pokemon.bff.client.dto.PokemonListResponse;
import com.pokemon.bff.client.dto.PokemonSpeciesResponse;
import com.pokemon.bff.dto.PokemonItem;
import com.pokemon.bff.sync.PokemonSnapshot;
import com.pokemon.bff.sync.PokemonSyncService;
import com.pokemon.bff.sync.PokemonSyncThrottle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Builds the entries of the Pokemon enumeration from the cached PokeAPI resources ({@link PokeApiCache}), the same
 * resources the detail view uses.
 */
@Service
public class PokemonCatalogService {
    private static final Logger log = LoggerFactory.getLogger(PokemonCatalogService.class);

    private final PokeApiCache pokeApi;
    private final PokemonSyncService syncService;
    private final PokemonSyncThrottle syncThrottle;

    public PokemonCatalogService(PokeApiCache pokeApi, PokemonSyncService syncService,
            PokemonSyncThrottle syncThrottle) {
        this.pokeApi = pokeApi;
        this.syncService = syncService;
        this.syncThrottle = syncThrottle;
    }

    public PokemonListResponse fetchReferences(int offset, int limit) {
        return pokeApi.references(offset, limit);
    }

    public PokemonItem fetchItem(String name) {
        return fetchItem(name, rateLimited -> { });
    }

    /**
     * Builds one entry. If {@code /pokemon} fails the whole entry fails (the caller degrades it to
     * {@code available=false}); if only the species fails, the entry keeps its image, mass and skills and is marked
     * {@code categoryAvailable=false}, as the detail view does with its description.
     *
     * @param onSpeciesRateLimited told when the species is missing because of PokeAPI's rate limit, so the page can
     *                             say when reloading helps
     */
    public PokemonItem fetchItem(String name, Consumer<PokeApiRateLimitException> onSpeciesRateLimited) {
        // No retries for page entries: a failed entry degrades instead of delaying the page.
        var details = pokeApi.pokemon(name, false);
        PokemonSpeciesResponse species;
        try {
            species = pokeApi.species(details.speciesKey(), false);
        } catch (PokeApiRateLimitException e) {
            onSpeciesRateLimited.accept(e);
            species = null;
        } catch (RuntimeException e) {
            log.warn("Could not load the species of Pokemon {}: {}", details.name(), e.getMessage());
            species = null;
        }
        if (species == null) {
            // Not stored either: a later read with the species writes the complete copy (and the sync throttle is
            // not spent on a partial one).
            return PokemonItem.withoutCategory(details.id(), details.name(), PokeApiMapper.image(details),
                    PokeApiMapper.mass(details), PokeApiMapper.skills(details));
        }
        var item = new PokemonItem(details.id(), details.name(), PokeApiMapper.image(details),
                PokeApiMapper.category(species), PokeApiMapper.mass(details), PokeApiMapper.skills(details));
        // Same data the detail view stores: both resources are already loaded, so this costs no extra call.
        syncQuietly(new PokemonSnapshot(details.id(), details.name(), item.image(), PokeApiMapper.height(details),
                item.mass(), item.category(), item.skills(), PokeApiMapper.stats(details),
                PokeApiMapper.description(species), true));
        return item;
    }

    /**
     * The local copy is a side benefit of reading and runs asynchronously, at most once per Pokemon per TTL; if the
     * sync queue is full the submission is rejected here and that sync is simply skipped.
     */
    private void syncQuietly(PokemonSnapshot snapshot) {
        if (!syncThrottle.tryAcquire(snapshot.id())) {
            return;
        }
        try {
            syncService.sync(snapshot);
        } catch (RuntimeException e) {
            log.warn("Could not synchronise Pokemon {} to the local store: {}", snapshot.name(), e.getMessage());
        }
    }
}
