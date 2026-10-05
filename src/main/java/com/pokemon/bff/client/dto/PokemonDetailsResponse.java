package com.pokemon.bff.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @param species the species this Pokemon belongs to. Alternate forms (e.g. {@code charizard-mega-x},
 *                {@code raichu-alola}) have their own name and id but share the species of the base Pokemon,
 *                and only the species name is accepted by {@code /pokemon-species}.
 */
public record PokemonDetailsResponse(int id, String name, int height, int weight, Sprites sprites,
                                     List<AbilitySlot> abilities, List<StatSlot> stats, PokemonReference species) {

    public PokemonDetailsResponse(int id, String name, int height, int weight, Sprites sprites,
            List<AbilitySlot> abilities, List<StatSlot> stats) {
        this(id, name, height, weight, sprites, abilities, stats, null);
    }

    /** Identifier to query {@code /pokemon-species} with: the species name, or the Pokemon's own name as a fallback. */
    public String speciesKey() {
        return species != null && species.name() != null && !species.name().isBlank() ? species.name() : name;
    }
    public record Sprites(@JsonProperty("front_default") String frontDefault) {}
    public record AbilitySlot(Ability ability) {}
    public record Ability(String name, String url) {}
    public record StatSlot(@JsonProperty("base_stat") int baseStat, Stat stat) {}
    public record Stat(String name, String url) {}
}
