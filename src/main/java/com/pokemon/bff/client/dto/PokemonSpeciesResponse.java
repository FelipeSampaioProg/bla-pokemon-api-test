package com.pokemon.bff.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;

/**
 * @param varieties the Pokemon of this species. The default one is not always named after the species
 *                  ({@code lycanroc} -> {@code lycanroc-midday}, {@code giratina} -> {@code giratina-altered}).
 */
public record PokemonSpeciesResponse(List<Genus> genera,
                                     @JsonProperty("flavor_text_entries") List<FlavorTextEntry> flavorTextEntries,
                                     @JsonProperty("evolution_chain") EvolutionChain evolutionChain,
                                     List<Variety> varieties) {

    public PokemonSpeciesResponse(List<Genus> genera, List<FlavorTextEntry> flavorTextEntries,
            EvolutionChain evolutionChain) {
        this(genera, flavorTextEntries, evolutionChain, null);
    }

    /** Name of the species' default Pokemon, if PokeAPI marked one. */
    public Optional<String> defaultPokemonName() {
        if (varieties == null) {
            return Optional.empty();
        }
        return varieties.stream()
                .filter(variety -> variety != null && variety.isDefault() && variety.pokemon() != null)
                .map(variety -> variety.pokemon().name())
                .filter(name -> name != null && !name.isBlank())
                .findFirst();
    }

    public record Variety(@JsonProperty("is_default") boolean isDefault, PokemonReference pokemon) {}
    public record Genus(String genus, Language language) {}
    public record FlavorTextEntry(@JsonProperty("flavor_text") String flavorText, Language language) {}
    public record EvolutionChain(String url) {}
    public record Language(String name) {}
}
