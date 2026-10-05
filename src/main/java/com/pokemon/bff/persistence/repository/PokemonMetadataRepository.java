package com.pokemon.bff.persistence.repository;

import com.pokemon.bff.persistence.entity.PokemonMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PokemonMetadataRepository extends JpaRepository<PokemonMetadataEntity, Long> {
    Optional<PokemonMetadataEntity> findByPokemonId(Integer pokemonId);
}
