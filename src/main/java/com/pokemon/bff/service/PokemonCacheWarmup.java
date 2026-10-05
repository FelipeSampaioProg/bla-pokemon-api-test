package com.pokemon.bff.service;

import com.pokemon.bff.config.PokemonCacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Preloads the first pages of the PokeAPI listing after startup so the first users do not pay the cold-cache cost.
 * <p>
 * Runs on its own background thread (it must not borrow a thread from the fetch pool, since {@code findPage}
 * submits its work there) and stops at the first failure: if PokeAPI is unavailable the normal request flow
 * handles it. Items are cached by name, so warming one page size also speeds up every other page size.
 */
@Component
public class PokemonCacheWarmup {
    private static final Logger log = LoggerFactory.getLogger(PokemonCacheWarmup.class);

    private final PokeApiPokemonService service;
    private final PokemonCacheProperties cacheProperties;
    private final boolean enabled;
    private final int pages;
    private final int pageSize;

    public PokemonCacheWarmup(PokeApiPokemonService service, PokemonCacheProperties cacheProperties,
            @Value("${pokemon.cache.warmup.enabled:true}") boolean enabled,
            @Value("${pokemon.cache.warmup.pages:5}") int pages,
            @Value("${pokemon.cache.warmup.page-size:20}") int pageSize) {
        this.service = service;
        this.cacheProperties = cacheProperties;
        this.enabled = enabled;
        this.pages = pages;
        this.pageSize = pageSize;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (shouldRun()) {
            Thread.ofPlatform().name("pokemon-cache-warmup").daemon(true).start(this::warmUp);
        }
    }

    boolean shouldRun() {
        return enabled && pages > 0 && cacheProperties.getProvider() != PokemonCacheProperties.Provider.NONE;
    }

    /** Loads pages {@code 0..pages-1} sequentially; returns how many pages were warmed. */
    int warmUp() {
        int warmed = 0;
        for (int page = 0; page < pages; page++) {
            try {
                var result = service.findPage(page, pageSize);
                warmed++;
                if (page + 1 >= result.totalPages()) {
                    break;
                }
            } catch (RuntimeException e) {
                log.warn("Cache warm-up stopped at page {}: {}", page, e.getMessage());
                break;
            }
        }
        log.info("Cache warm-up finished: {} page(s) of {} Pokemon loaded", warmed, pageSize);
        return warmed;
    }
}
