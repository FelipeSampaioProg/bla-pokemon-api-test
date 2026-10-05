package com.pokemon.bff.service;

import com.pokemon.bff.dto.PokemonStat;
import com.pokemon.bff.dto.Skill;
import com.pokemon.bff.exception.InvalidFieldException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests of the field rules shared by the local writes (User Story 04). */
class PokemonWriteValidatorTest {

    private static void assertInvalid(String field, Executable check) {
        var error = assertThrows(InvalidFieldException.class, check);
        assertEquals(field, error.getField(), error.getMessage());
    }

    @Test
    void shouldRequireAPositiveId() {
        assertDoesNotThrow(() -> PokemonWriteValidator.id(1));
        assertInvalid("id", () -> PokemonWriteValidator.id(null));
        assertInvalid("id", () -> PokemonWriteValidator.id(0));
        assertInvalid("id", () -> PokemonWriteValidator.id(-5));
    }

    @Test
    void shouldAcceptABodyIdOnlyWhenItIsThePathId() {
        assertDoesNotThrow(() -> PokemonWriteValidator.bodyIdMatchesPath(null, 9001));
        assertDoesNotThrow(() -> PokemonWriteValidator.bodyIdMatchesPath(9001, 9001));
        assertInvalid("id", () -> PokemonWriteValidator.bodyIdMatchesPath(1, 9001));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pikachu", "Mr-Mime", " porygon-z ", "porygon2", "ho-oh"})
    void shouldAcceptPokeApiStyleNames(String name) {
        assertDoesNotThrow(() -> PokemonWriteValidator.name(name, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "mr mime", "mr.mime", "pika/chu", "-pikachu", "pikachu-", "pika--chu", "123"})
    void shouldRejectNamesThatCannotBeUsedInTheUrl(String name) {
        assertInvalid("name", () -> PokemonWriteValidator.name(name, true));
    }

    @Test
    void shouldRequireTheNameOnlyWhenAsked() {
        assertDoesNotThrow(() -> PokemonWriteValidator.name(null, false));
        assertInvalid("name", () -> PokemonWriteValidator.name(null, true));
        assertInvalid("name", () -> PokemonWriteValidator.name("a".repeat(101), false));
        assertDoesNotThrow(() -> PokemonWriteValidator.name("a".repeat(100), false));
    }

    @Test
    void shouldAcceptOnlyAbsoluteHttpUrlsForTheImage() {
        assertDoesNotThrow(() -> PokemonWriteValidator.image(null));
        assertDoesNotThrow(() -> PokemonWriteValidator.image(" "));
        assertDoesNotThrow(() -> PokemonWriteValidator.image("https://img.example/a.png"));
        assertDoesNotThrow(() -> PokemonWriteValidator.image("http://img.example/a.png"));
        assertInvalid("image", () -> PokemonWriteValidator.image("img.png"));
        assertInvalid("image", () -> PokemonWriteValidator.image("javascript:alert(1)"));
        assertInvalid("image", () -> PokemonWriteValidator.image("ftp://img.example/a.png"));
        assertInvalid("image", () -> PokemonWriteValidator.image("https://exa mple.com/a.png"));
        assertInvalid("image", () -> PokemonWriteValidator.image("https://img.example/" + "a".repeat(500)));
    }

    @Test
    void shouldLimitTheTexts() {
        assertDoesNotThrow(() -> PokemonWriteValidator.description(null));
        assertDoesNotThrow(() -> PokemonWriteValidator.description("d".repeat(5000)));
        assertInvalid("description", () -> PokemonWriteValidator.description("d".repeat(5001)));
        assertDoesNotThrow(() -> PokemonWriteValidator.category(" " + "c".repeat(100) + " "));
        assertInvalid("category", () -> PokemonWriteValidator.category("c".repeat(101)));
        assertDoesNotThrow(() -> PokemonWriteValidator.proprietaryFields("l".repeat(200), "r".repeat(100), null));
        assertInvalid("localizedName", () -> PokemonWriteValidator.proprietaryFields("l".repeat(201), null, null));
        assertInvalid("region", () -> PokemonWriteValidator.proprietaryFields(null, "r".repeat(101), null));
        assertInvalid("classificationTag", () -> PokemonWriteValidator.proprietaryFields(null, null, "t".repeat(101)));
    }

    @Test
    void shouldKeepMeasuresFiniteAndWithinTheirCeilings() {
        assertDoesNotThrow(() -> PokemonWriteValidator.height(null));
        assertDoesNotThrow(() -> PokemonWriteValidator.height(1000.0));
        assertDoesNotThrow(() -> PokemonWriteValidator.mass(0.0));
        assertDoesNotThrow(() -> PokemonWriteValidator.mass(10000.0));
        assertInvalid("height", () -> PokemonWriteValidator.height(1000.1));
        assertInvalid("height", () -> PokemonWriteValidator.height(-0.1));
        assertInvalid("height", () -> PokemonWriteValidator.height(Double.NaN));
        assertInvalid("mass", () -> PokemonWriteValidator.mass(Double.POSITIVE_INFINITY));
        assertInvalid("mass", () -> PokemonWriteValidator.mass(10000.5));
    }

    @Test
    void shouldCheckEveryStat() {
        assertDoesNotThrow(() -> PokemonWriteValidator.stats(null));
        assertDoesNotThrow(() -> PokemonWriteValidator.stats(List.of(new PokemonStat("hp", 0), new PokemonStat("speed", 255))));
        assertInvalid("stats", () -> PokemonWriteValidator.stats(
                IntStream.range(0, 21).mapToObj(i -> new PokemonStat("s" + i, 1)).toList()));
        assertInvalid("stats[0]", () -> PokemonWriteValidator.stats(Collections.singletonList(null)));
        assertInvalid("stats[1].name", () -> PokemonWriteValidator.stats(List.of(new PokemonStat("hp", 1), new PokemonStat(null, 1))));
        assertInvalid("stats[0].name", () -> PokemonWriteValidator.stats(List.of(new PokemonStat("s".repeat(101), 1))));
        assertInvalid("stats[0].value", () -> PokemonWriteValidator.stats(List.of(new PokemonStat("hp", 256))));
        assertInvalid("stats[0].value", () -> PokemonWriteValidator.stats(List.of(new PokemonStat("hp", -1))));
        assertInvalid("stats", () -> PokemonWriteValidator.stats(List.of(new PokemonStat("hp", 1), new PokemonStat(" HP ", 2))));
    }

    @Test
    void shouldCheckEverySkill() {
        assertDoesNotThrow(() -> PokemonWriteValidator.skills(null));
        assertDoesNotThrow(() -> PokemonWriteValidator.skills(List.of(new Skill("static", null),
                new Skill("lightning-rod", "https://pokeapi.co/api/v2/ability/31/"))));
        assertInvalid("skills", () -> PokemonWriteValidator.skills(
                IntStream.range(0, 21).mapToObj(i -> new Skill("s" + i, null)).toList()));
        assertInvalid("skills[0]", () -> PokemonWriteValidator.skills(Collections.singletonList(null)));
        assertInvalid("skills[0].name", () -> PokemonWriteValidator.skills(List.of(new Skill("", null))));
        assertInvalid("skills[0].url", () -> PokemonWriteValidator.skills(List.of(new Skill("static", "u-static"))));
        assertInvalid("skills", () -> PokemonWriteValidator.skills(List.of(new Skill("static", null), new Skill("Static", null))));
    }
}
