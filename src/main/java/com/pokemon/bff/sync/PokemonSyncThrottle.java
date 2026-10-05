package com.pokemon.bff.sync;

import com.pokemon.bff.config.PokemonCacheNames;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Lets each Pokemon be synchronised to the local store at most once per cache TTL, for the list entry and the
 * detail view alike. Without it, every detail view wrote to the database even when nothing had been fetched.
 * <p>
 * Backed by the {@code pokemonSync} cache, so with Redis the limit is shared by every instance. With caching
 * disabled ({@code POKEMON_CACHE_PROVIDER=none}) there is nothing to remember and every read synchronises.
 */
@Component
public class PokemonSyncThrottle {
    private final CacheManager cacheManager;

    public PokemonSyncThrottle(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Returns {@code true} for the first call per Pokemon within the TTL. The list entry and the detail view store
     * the same snapshot, so one mark covers both.
     */
    public boolean tryAcquire(int pokemonId) {
        Cache marks = cacheManager.getCache(PokemonCacheNames.POKEMON_SYNC);
        if (marks == null) {
            return true;
        }
        return marks.putIfAbsent(String.valueOf(pokemonId), Boolean.TRUE) == null;
    }
}
