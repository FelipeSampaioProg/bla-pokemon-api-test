package com.pokemon.bff.service;

import java.util.Locale;

public final class PokemonCacheKeys {
    private PokemonCacheKeys() {
    }

    public static String references(int offset, int limit) {
        return offset + ":" + limit;
    }

    public static String pokemon(String pokemon) {
        return pokemon == null ? "" : pokemon.trim().toLowerCase(Locale.ROOT);
    }
}
