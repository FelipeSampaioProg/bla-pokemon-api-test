package com.pokemon.bff.service;

import com.pokemon.bff.client.dto.PokemonDetailsResponse;
import com.pokemon.bff.client.dto.PokemonSpeciesResponse;
import com.pokemon.bff.dto.PokemonStat;
import com.pokemon.bff.dto.Skill;

import java.util.List;

/**
 * Turns PokeAPI resources into the values shown to users. Shared by the list entry and the detail view so the
 * same field is always computed the same way (PokeAPI reports decimetres and hectograms; we show m and kg).
 */
final class PokeApiMapper {
    private PokeApiMapper() {
    }

    static String image(PokemonDetailsResponse details) {
        return details.sprites() == null ? null : details.sprites().frontDefault();
    }

    static double height(PokemonDetailsResponse details) {
        return details.height() / 10.0;
    }

    static double mass(PokemonDetailsResponse details) {
        return details.weight() / 10.0;
    }

    static List<Skill> skills(PokemonDetailsResponse details) {
        return details.abilities() == null ? List.of() : details.abilities().stream()
                .filter(slot -> slot != null && slot.ability() != null)
                .map(slot -> new Skill(slot.ability().name(), slot.ability().url()))
                .toList();
    }

    static List<PokemonStat> stats(PokemonDetailsResponse details) {
        return details.stats() == null ? List.of() : details.stats().stream()
                .filter(slot -> slot != null && slot.stat() != null)
                .map(slot -> new PokemonStat(slot.stat().name(), slot.baseStat()))
                .toList();
    }

    static String category(PokemonSpeciesResponse species) {
        if (species == null || species.genera() == null) {
            return null;
        }
        return species.genera().stream()
                .filter(genus -> genus != null && genus.language() != null && "en".equals(genus.language().name()))
                .map(PokemonSpeciesResponse.Genus::genus)
                .findFirst()
                .orElse(null);
    }

    static String description(PokemonSpeciesResponse species) {
        if (species == null || species.flavorTextEntries() == null) {
            return null;
        }
        return species.flavorTextEntries().stream()
                .filter(entry -> entry != null
                        && entry.language() != null
                        && "en".equals(entry.language().name())
                        && entry.flavorText() != null
                        && !entry.flavorText().isBlank())
                .map(entry -> entry.flavorText().replace('\n', ' ')
                        .replace('\f', ' ')
                        .replace('\t', ' ')
                        .replaceAll("\\s+", " ")
                        .trim())
                .findFirst()
                .orElse(null);
    }
}
