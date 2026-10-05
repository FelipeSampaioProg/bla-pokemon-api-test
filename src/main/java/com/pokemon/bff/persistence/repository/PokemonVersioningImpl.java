package com.pokemon.bff.persistence.repository;

import com.pokemon.bff.persistence.entity.PokemonEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/** Picked up by Spring Data by name as the implementation of the {@link PokemonVersioning} fragment. */
class PokemonVersioningImpl implements PokemonVersioning {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void forceVersionIncrement(PokemonEntity entity) {
        entityManager.flush();
        // Runs "update pokemon set version = version + 1 where id = ? and version = ?" right away (a concurrent write
        // fails here with an optimistic lock error) and updates the version of the entity in memory.
        entityManager.lock(entity, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }
}
