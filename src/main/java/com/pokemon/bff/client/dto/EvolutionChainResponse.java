package com.pokemon.bff.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record EvolutionChainResponse(ChainLink chain) {
    public record ChainLink(PokemonReference species, @JsonProperty("evolves_to") List<ChainLink> evolvesTo) {
    }
}
