package com.pokemon.bff.service;

import com.pokemon.bff.config.PokemonCacheProperties;
import com.pokemon.bff.dto.PokemonPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PokemonCacheWarmupTest {

    @Mock
    private PokeApiPokemonService service;

    private final PokemonCacheProperties cacheProperties = new PokemonCacheProperties();

    @Test
    void shouldWarmConfiguredNumberOfPages() {
        when(service.findPage(anyInt(), eq(20))).thenAnswer(inv -> page(inv.getArgument(0), 66));

        int warmed = warmup(true, 3).warmUp();

        assertEquals(3, warmed);
        verify(service).findPage(0, 20);
        verify(service).findPage(1, 20);
        verify(service).findPage(2, 20);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldStopAtLastAvailablePage() {
        when(service.findPage(anyInt(), eq(20))).thenAnswer(inv -> page(inv.getArgument(0), 2));

        assertEquals(2, warmup(true, 5).warmUp());
        verify(service, never()).findPage(2, 20);
    }

    @Test
    void shouldStopAtFirstUpstreamFailure() {
        when(service.findPage(0, 20)).thenReturn(page(0, 66));
        when(service.findPage(1, 20)).thenThrow(new IllegalStateException("PokeAPI down"));

        assertEquals(1, warmup(true, 5).warmUp());
        verify(service, never()).findPage(2, 20);
    }

    @Test
    void shouldNotRunWhenDisabledWithoutPagesOrWithoutCache() {
        assertTrue(warmup(true, 5).shouldRun());
        assertFalse(warmup(false, 5).shouldRun());
        assertFalse(warmup(true, 0).shouldRun());

        cacheProperties.setProvider(PokemonCacheProperties.Provider.NONE);
        assertFalse(warmup(true, 5).shouldRun());
    }

    private PokemonCacheWarmup warmup(boolean enabled, int pages) {
        return new PokemonCacheWarmup(service, cacheProperties, enabled, pages, 20);
    }

    private static PokemonPage page(int page, int totalPages) {
        return new PokemonPage(List.of(), page, 20, totalPages * 20, totalPages);
    }
}
