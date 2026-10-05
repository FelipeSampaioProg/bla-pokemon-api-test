package com.pokemon.bff.persistence.repository;

import com.pokemon.bff.persistence.entity.PokemonEntity;

/** Repository fragment of {@link PokemonRepository}. */
public interface PokemonVersioning {

    /**
     * Flushes the pending changes of a managed Pokemon and increments its version now. Hibernate only increments the
     * version by itself when a column of the {@code pokemon} row changes; a write that only changes child rows (the
     * proprietary fields in {@code pokemon_metadata}, stats, skills) would keep the version, and with it the
     * {@code ETag}, so a stale {@code If-Match} would still be accepted. Must run inside a transaction.
     */
    void forceVersionIncrement(PokemonEntity entity);
}
