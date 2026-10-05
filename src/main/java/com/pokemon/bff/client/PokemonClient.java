package com.pokemon.bff.client;

import com.pokemon.bff.client.dto.EvolutionChainResponse;
import com.pokemon.bff.client.dto.PokemonDetailsResponse;
import com.pokemon.bff.client.dto.PokemonListResponse;
import com.pokemon.bff.client.dto.PokemonSpeciesResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "pokeApiClient", url = "${pokeapi.url}")
public interface PokemonClient {
    @GetMapping("/pokemon")
    PokemonListResponse findAll(@RequestParam("offset") int offset, @RequestParam("limit") int limit);

    @GetMapping("/pokemon/{pokemon}")
    PokemonDetailsResponse findByNameOrId(@PathVariable("pokemon") String pokemon);

    @GetMapping("/pokemon-species/{pokemon}")
    PokemonSpeciesResponse findSpeciesByNameOrId(@PathVariable("pokemon") String pokemon);

    @GetMapping("/evolution-chain/{id}")
    EvolutionChainResponse findEvolutionChainById(@PathVariable("id") int id);

}
