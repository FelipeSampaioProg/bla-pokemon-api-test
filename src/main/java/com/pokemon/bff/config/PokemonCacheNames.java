package com.pokemon.bff.config;

public final class PokemonCacheNames {
    /** Upstream list references (names/urls) for a given offset and limit. */
    public static final String POKEMON_PAGE = "pokemonPage";
    /**
     * Raw PokeAPI resources. The list entry and the detail view are both built from these, so they reuse the same
     * calls and always show the same data for a Pokemon.
     */
    public static final String POKEAPI_POKEMON = "pokeapiPokemon";
    public static final String POKEAPI_SPECIES = "pokeapiSpecies";
    public static final String POKEAPI_EVOLUTION = "pokeapiEvolution";
    /** Marks of recent local-store synchronisations, so a Pokemon is synchronised at most once per TTL. */
    public static final String POKEMON_SYNC = "pokemonSync";

    private PokemonCacheNames() {
    }
}
