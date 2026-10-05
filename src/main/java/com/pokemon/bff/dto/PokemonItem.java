package com.pokemon.bff.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * List entry for the Pokemon enumeration.
 *
 * @param available         {@code false} when the upstream data for this entry could not be loaded; only
 *                          {@code id} and {@code name} are filled in that case so the rest of the page is still
 *                          usable.
 * @param categoryAvailable {@code false} when only the species (which holds the category) could not be loaded: the
 *                          image, mass and skills are still shown, as in the detail view
 *                          ({@code descriptionAvailable}). A {@code null} category with this flag {@code true} means
 *                          PokeAPI has no English category.
 * @param metadata          proprietary fields added locally; present only in the local listing, when set.
 */
public record PokemonItem(int id, String name, String image, String category,
                          Double mass, List<Skill> skills, boolean available, boolean categoryAvailable,
                          @JsonInclude(JsonInclude.Include.NON_NULL) PokemonMetadata metadata) {

    public PokemonItem(int id, String name, String image, String category, Double mass, List<Skill> skills,
            boolean available, PokemonMetadata metadata) {
        this(id, name, image, category, mass, skills, available, available, metadata);
    }

    public PokemonItem(int id, String name, String image, String category, Double mass, List<Skill> skills,
            boolean available) {
        this(id, name, image, category, mass, skills, available, null);
    }

    public PokemonItem(int id, String name, String image, String category, Double mass, List<Skill> skills) {
        this(id, name, image, category, mass, skills, true);
    }

    public PokemonItem {
        if (skills == null) {
            skills = List.of();
        }
    }

    public static PokemonItem unavailable(int id, String name) {
        return new PokemonItem(id, name, null, null, null, List.of(), false);
    }

    /** An entry whose species could not be loaded: everything but the category. */
    public static PokemonItem withoutCategory(int id, String name, String image, Double mass, List<Skill> skills) {
        return new PokemonItem(id, name, image, null, mass, skills, true, false, null);
    }

    /** True when part of the entry could not be loaded from PokeAPI. */
    public boolean partial() {
        return !available || !categoryAvailable;
    }
}
