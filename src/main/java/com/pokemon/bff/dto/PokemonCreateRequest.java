package com.pokemon.bff.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * Fields follow the names used by every response ({@code image}, {@code mass}). The former names
 * {@code imageUrl} and {@code weight} are still accepted on input for backward compatibility.
 */
public record PokemonCreateRequest(
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
    /** Constructor for payloads without category. */
    public PokemonCreateRequest(Integer id, String name, String image, Double height, Double mass,
            String description, List<PokemonStat> stats, List<Skill> skills,
            String localizedName, String region, String classificationTag) {
        this(id, name, image, height, mass, description, null, stats, skills,
                localizedName, region, classificationTag);
    }

    /** Backward-compatible constructor for payloads without skills (record-order parameters). */
    public PokemonCreateRequest(Integer id, String name, String image, Double height,
            Double mass, String description, List<PokemonStat> stats,
            String localizedName, String region, String classificationTag) {
        this(id, name, image, height, mass, description, stats, List.of(),
                localizedName, region, classificationTag);
    }

    public PokemonCreateRequest {
        if (stats == null) {
            stats = List.of();
        }
        if (skills == null) {
            skills = List.of();
        }
    }
}
