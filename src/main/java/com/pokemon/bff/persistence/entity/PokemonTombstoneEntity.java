package com.pokemon.bff.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Mark left by a {@code DELETE}: the automatic PokeAPI synchronisation does not recreate a Pokemon that has one, so a
 * deleted record stays deleted. An explicit {@code POST} or {@code POST /{nameOrId}/sync} removes it.
 */
@Entity
@Table(name = "pokemon_tombstone")
@Getter
@Setter
@NoArgsConstructor
public class PokemonTombstoneEntity {

    @Id
    @Column(name = "pokemon_id")
    private Integer pokemonId;

    @Column(name = "deleted_at", nullable = false)
    private Instant deletedAt;

    public PokemonTombstoneEntity(Integer pokemonId, Instant deletedAt) {
        this.pokemonId = pokemonId;
        this.deletedAt = deletedAt;
    }
}
