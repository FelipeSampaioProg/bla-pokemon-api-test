package com.pokemon.bff.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * Body of {@code PATCH /api/v1/local-pokemons/{id}}: only the fields sent change. Fields follow the names used by
 * every response ({@code image}, {@code mass}); the former names {@code imageUrl} and {@code weight} are still
 * accepted on input for backward compatibility.
 *
 * @param id optional; the id comes from the path, and one sent here must be the same (it is never changed)
 */
public record PokemonUpdateRequest(
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

    /** Update without an id in the body (the usual case: the id is in the path). */
    public PokemonUpdateRequest(String name, String image, Double height, Double mass, String description,
            String category, List<PokemonStat> stats, List<Skill> skills, String localizedName, String region,
            String classificationTag) {
        this(null, name, image, height, mass, description, category, stats, skills, localizedName, region,
                classificationTag);
    }

    /** Constructor for updates without category. */
    public PokemonUpdateRequest(String name, String image, Double height, Double mass, String description,
            List<PokemonStat> stats, List<Skill> skills, String localizedName, String region,
            String classificationTag) {
        this(name, image, height, mass, description, null, stats, skills,
                localizedName, region, classificationTag);
    }

    /** Convenience constructor for updates that only use the basic fields. */
    public PokemonUpdateRequest(String name, String description, String image,
            String localizedName, String region, String classificationTag) {
        this(name, image, null, null, description, null, null,
                localizedName, region, classificationTag);
    }

    /**
     * Whether the request changes data that comes from PokeAPI. Such a change takes ownership of the record (the
     * synchronisation stops updating it); a change limited to the proprietary fields does not.
     */
    public boolean touchesReplicatedFields() {
        return name != null || image != null || height != null || mass != null || description != null
                || category != null || stats != null || skills != null;
    }

    /** Whether the request only sets proprietary fields (localized name, region, classification tag). */
    public boolean onlyProprietaryFields() {
        return !touchesReplicatedFields()
                && (localizedName != null || region != null || classificationTag != null);
    }
}
