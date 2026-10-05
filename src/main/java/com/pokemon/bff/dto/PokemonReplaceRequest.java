package com.pokemon.bff.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * Body of {@code PUT /api/v1/local-pokemons/{id}}: the complete new state of a local Pokemon. Unlike {@code PATCH}
 * ({@link PokemonUpdateRequest}), a field left out is cleared, not kept. Only {@code name} is required; the id comes
 * from the path, and an {@code id} sent in the body must be the same.
 */
public record PokemonReplaceRequest(
        Integer id,
        String name,
        @JsonAlias("imageUrl") String image,
        Double height,
        @JsonAlias("weight") Double mass,
        String description,
        String category,
        List<PokemonStat> stats,
        List<Skill> skills,
        String localizedName,
        String region,
        String classificationTag
) {
    /** Replacement without an id in the body (the usual case: the id is in the path). */
    public PokemonReplaceRequest(String name, String image, Double height, Double mass, String description,
            String category, List<PokemonStat> stats, List<Skill> skills, String localizedName, String region,
            String classificationTag) {
        this(null, name, image, height, mass, description, category, stats, skills, localizedName, region,
                classificationTag);
    }

    public PokemonReplaceRequest {
        if (stats == null) {
            stats = List.of();
        }
        if (skills == null) {
            skills = List.of();
        }
    }
}
