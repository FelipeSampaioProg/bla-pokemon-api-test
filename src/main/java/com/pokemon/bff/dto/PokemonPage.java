package com.pokemon.bff.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One page of Pokemon.
 *
 * @param retryAfterSeconds present only when entries of the page are unavailable because PokeAPI is rate limiting
 *                          us: the number of seconds after which reloading the page should bring them back. Also sent
 *                          as the {@code Retry-After} header.
 */
public record PokemonPage(List<PokemonItem> content, int page, int size, int totalElements, int totalPages,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Integer retryAfterSeconds) {

    public PokemonPage(List<PokemonItem> content, int page, int size, int totalElements, int totalPages) {
        this(content, page, size, totalElements, totalPages, null);
    }
}
