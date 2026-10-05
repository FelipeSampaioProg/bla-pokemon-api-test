package com.pokemon.bff.persistence.repository;

import com.pokemon.bff.persistence.entity.PokemonTombstoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PokemonTombstoneRepository extends JpaRepository<PokemonTombstoneEntity, Integer> {
}
