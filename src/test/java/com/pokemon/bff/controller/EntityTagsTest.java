package com.pokemon.bff.controller;

import com.pokemon.bff.service.VersionCondition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests of the HTTP side of optimistic concurrency: ETag and If-Match (RFC 9110). */
class EntityTagsTest {

    @Test
    void shouldFormatAVersionAsAStrongEntityTag() {
        assertEquals("\"3-1700\"", EntityTags.format("3-1700"));
        assertNull(EntityTags.format(null));
    }

    @Test
    void shouldSetNoConditionWithoutIfMatch() {
        assertNull(EntityTags.parseIfMatch(null));
        assertNull(EntityTags.parseIfMatch("  "));
    }

    @Test
    void shouldReadTheStarAsAnyVersion() {
        assertEquals(VersionCondition.any(), EntityTags.parseIfMatch(" * "));
    }

    @Test
    void shouldReadEveryStrongTagOfTheList() {
        assertEquals(VersionCondition.oneOf(List.of("1-1", "3-1700")), EntityTags.parseIfMatch("\"1-1\", \"3-1700\""));
    }

    @Test
    void shouldNeverMatchWeakOrMalformedTags() {
        // Strong comparison: a weak tag never matches, and a header with no tag cannot be satisfied.
        assertEquals(VersionCondition.oneOf(List.of("2-1")), EntityTags.parseIfMatch("W/\"3-1700\", \"2-1\""));
        assertFalse(EntityTags.parseIfMatch("W/\"3-1700\"").isSatisfiedBy("3-1700"));
        assertFalse(EntityTags.parseIfMatch("garbage").isSatisfiedBy("3-1700"));
    }

    @Test
    void shouldRoundTripAVersion() {
        var condition = EntityTags.parseIfMatch(EntityTags.format("3-1700"));

        assertTrue(condition.isSatisfiedBy("3-1700"));
        assertFalse(condition.isSatisfiedBy("4-1700"));
    }

    @Test
    void shouldCompareVersionConditionsByValue() {
        assertTrue(VersionCondition.any().isSatisfiedBy("anything"));
        assertEquals(Set.of(), new VersionCondition(false, null).versions());
        assertFalse(VersionCondition.oneOf(List.of()).isSatisfiedBy("3-1700"));
    }
}
