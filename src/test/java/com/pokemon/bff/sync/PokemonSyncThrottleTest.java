package com.pokemon.bff.sync;

import com.pokemon.bff.config.PokemonCacheNames;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokemonSyncThrottleTest {

    @Test
    void shouldLetOnlyTheFirstSyncPerPokemonThrough() {
        var throttle = new PokemonSyncThrottle(new ConcurrentMapCacheManager(PokemonCacheNames.POKEMON_SYNC));

        assertTrue(throttle.tryAcquire(25));
        assertFalse(throttle.tryAcquire(25));
        assertTrue(throttle.tryAcquire(26));
    }

    @Test
    void shouldAlwaysSyncWhenCachingIsDisabled() {
        var throttle = new PokemonSyncThrottle(new NoOpCacheManager());

        assertTrue(throttle.tryAcquire(25));
        assertTrue(throttle.tryAcquire(25));
    }
}
