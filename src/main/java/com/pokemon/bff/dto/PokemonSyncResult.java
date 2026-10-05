package com.pokemon.bff.dto;

/**
 * Answer of {@code POST /api/v1/local-pokemons/{nameOrId}/sync}.
 *
 * @param outcome what the synchronisation did
 * @param pokemon the local record as it stands afterwards
 */
public record PokemonSyncResult(Outcome outcome, PokemonDetail pokemon) {

    public enum Outcome {
        /** The Pokemon was not in the local store and was replicated from PokeAPI. */
        CREATED,
        /** The local copy was refreshed with the current PokeAPI data (proprietary fields kept). */
        UPDATED,
        /** The record holds local changes to PokeAPI fields, which win: nothing was overwritten. */
        KEPT_LOCAL_CHANGES
    }
}
