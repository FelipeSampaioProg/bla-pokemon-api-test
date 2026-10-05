package com.pokemon.bff.persistence.repository;

import com.pokemon.bff.persistence.entity.PokemonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PokemonRepository extends JpaRepository<PokemonEntity, Integer>, PokemonVersioning {
    Optional<PokemonEntity> findByName(String name);

    List<PokemonEntity> findByNameContainingIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
