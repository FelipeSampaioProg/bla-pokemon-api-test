package com.pokemon.bff.client.dto;

import java.util.List;

public record PokemonListResponse(int count, List<PokemonReference> results) {
}
