package com.pokemon.bff.service;

import com.pokemon.bff.client.dto.PokemonDetailsResponse;
import com.pokemon.bff.client.dto.PokemonDetailsResponse.Ability;
import com.pokemon.bff.client.dto.PokemonDetailsResponse.AbilitySlot;
import com.pokemon.bff.client.dto.PokemonDetailsResponse.Stat;
import com.pokemon.bff.client.dto.PokemonDetailsResponse.StatSlot;
import com.pokemon.bff.client.dto.PokemonSpeciesResponse;
import com.pokemon.bff.client.dto.PokemonSpeciesResponse.FlavorTextEntry;
import com.pokemon.bff.client.dto.PokemonSpeciesResponse.Genus;
import com.pokemon.bff.client.dto.PokemonSpeciesResponse.Language;
import com.pokemon.bff.dto.PokemonStat;
import com.pokemon.bff.dto.Skill;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests of how PokeAPI resources become the values shown to users (shared by the card and the detail). */
class PokeApiMapperTest {
    private static final Language EN = new Language("en");
    private static final Language JA = new Language("ja");

    private static PokemonDetailsResponse details(PokemonDetailsResponse.Sprites sprites, List<AbilitySlot> abilities,
            List<StatSlot> stats) {
        return new PokemonDetailsResponse(25, "pikachu", 4, 60, sprites, abilities, stats);
    }

    @Test
    void shouldConvertDecimetresAndHectogramsToMetresAndKilograms() {
        var pikachu = details(null, null, null);

        assertEquals(0.4, PokeApiMapper.height(pikachu));
        assertEquals(6.0, PokeApiMapper.mass(pikachu));
    }

    @Test
    void shouldReadTheImageWhenThereIsOne() {
        assertEquals("p.png", PokeApiMapper.image(details(new PokemonDetailsResponse.Sprites("p.png"), null, null)));
        assertNull(PokeApiMapper.image(details(null, null, null)));
    }

    @Test
    void shouldSkipIncompleteAbilityAndStatSlots() {
        var pikachu = details(null,
                Arrays.asList(new AbilitySlot(new Ability("static", "u-9")), null, new AbilitySlot(null)),
                Arrays.asList(new StatSlot(35, new Stat("hp", "u")), null, new StatSlot(90, null)));

        assertEquals(List.of(new Skill("static", "u-9")), PokeApiMapper.skills(pikachu));
        assertEquals(List.of(new PokemonStat("hp", 35)), PokeApiMapper.stats(pikachu));
        assertEquals(List.of(), PokeApiMapper.skills(details(null, null, null)));
        assertEquals(List.of(), PokeApiMapper.stats(details(null, null, null)));
    }

    @Test
    void shouldTakeTheEnglishCategory() {
        var species = new PokemonSpeciesResponse(Arrays.asList(null, new Genus("Maus", new Language("de")),
                new Genus("Nezumi", null), new Genus("Mouse Pokemon", EN)), List.of(), null);

        assertEquals("Mouse Pokemon", PokeApiMapper.category(species));
        assertNull(PokeApiMapper.category(null));
        assertNull(PokeApiMapper.category(new PokemonSpeciesResponse(null, null, null)));
        assertNull(PokeApiMapper.category(new PokemonSpeciesResponse(List.of(new Genus("Nezumi", JA)), null, null)));
    }

    @Test
    void shouldTakeTheFirstEnglishDescriptionOnOneLine() {
        var species = new PokemonSpeciesResponse(List.of(), Arrays.asList(
                null,
                new FlavorTextEntry("Pikachu desu.", JA),
                new FlavorTextEntry("  ", EN),
                new FlavorTextEntry(null, EN),
                new FlavorTextEntry("Missing language", null),
                new FlavorTextEntry("When several\nof these\fPokemon\tgather,   their electricity", EN),
                new FlavorTextEntry("A later English text.", EN)), null);

        assertEquals("When several of these Pokemon gather, their electricity", PokeApiMapper.description(species));
        assertNull(PokeApiMapper.description(null));
        assertNull(PokeApiMapper.description(new PokemonSpeciesResponse(null, null, null)));
    }
}
