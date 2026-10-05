package com.pokemon.bff.sync;

import com.pokemon.bff.dto.PokemonDetail;
import com.pokemon.bff.dto.PokemonStat;
import com.pokemon.bff.dto.Skill;

import java.util.List;

/**
 * What is copied to the local store from a PokeAPI read. The list entry and the detail view build the same
 * snapshot (both read {@code /pokemon} and {@code /pokemon-species}), so a Pokemon stored by either one shows the
 * same data in {@code /api/v1/local-pokemons}.
 *
 * @param speciesAvailable {@code false} when the species could not be loaded: category and description are then
 *                         unknown and the values already stored are kept.
 */
public record PokemonSnapshot(int id, String name, String image, Double height, Double mass, String category,
                              List<Skill> skills, List<PokemonStat> stats, String description,
                              boolean speciesAvailable) {

    public PokemonSnapshot {
        if (skills == null) {
            skills = List.of();
        }
        if (stats == null) {
            stats = List.of();
        }
    }

    public static PokemonSnapshot from(PokemonDetail detail) {
        return new PokemonSnapshot(detail.id(), detail.name(), detail.image(), detail.height(), detail.mass(),
                detail.category(), detail.skills(), detail.stats(), detail.description(),
                detail.descriptionAvailable());
    }
}
