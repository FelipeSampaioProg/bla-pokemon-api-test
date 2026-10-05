package com.pokemon.bff.dto;

import java.util.List;

public record EvolutionNode(String name, List<EvolutionNode> evolvesTo) {
}
