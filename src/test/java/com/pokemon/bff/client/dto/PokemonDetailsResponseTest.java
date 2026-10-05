package com.pokemon.bff.client.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokemonDetailsResponseTest {
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void shouldReadSpeciesOfAlternateFormFromPokeApiPayload() {
        var json = """
                {"id": 10034, "name": "charizard-mega-x", "height": 17, "weight": 1105,
                 "sprites": {"front_default": "mega-x.png", "back_default": null},
                 "abilities": [], "stats": [],
                 "species": {"name": "charizard", "url": "https://pokeapi.co/api/v2/pokemon-species/6/"},
                 "base_experience": 285}
                """;

        var response = mapper.readValue(json, PokemonDetailsResponse.class);

        assertEquals("charizard", response.species().name());
        assertEquals("charizard", response.speciesKey());
        assertEquals("mega-x.png", response.sprites().frontDefault());
    }

    @Test
    void shouldReadDefaultVarietyOfSpeciesFromPokeApiPayload() {
        var json = """
                {"name": "lycanroc", "genera": [], "flavor_text_entries": [],
                 "evolution_chain": {"url": "https://pokeapi.co/api/v2/evolution-chain/372/"},
                 "varieties": [
                   {"is_default": true, "pokemon": {"name": "lycanroc-midday", "url": "u1"}},
                   {"is_default": false, "pokemon": {"name": "lycanroc-midnight", "url": "u2"}}]}
                """;

        var species = mapper.readValue(json, PokemonSpeciesResponse.class);

        assertEquals("lycanroc-midday", species.defaultPokemonName().orElseThrow());
    }

    @Test
    void shouldHaveNoDefaultPokemonWithoutVarieties() {
        assertTrue(new PokemonSpeciesResponse(null, null, null).defaultPokemonName().isEmpty());
    }

    @Test
    void shouldFallBackToPokemonNameWhenSpeciesIsAbsent() {
        var response = new PokemonDetailsResponse(25, "pikachu", 4, 60, null, null, null);

        assertNull(response.species());
        assertEquals("pikachu", response.speciesKey());
    }
}
