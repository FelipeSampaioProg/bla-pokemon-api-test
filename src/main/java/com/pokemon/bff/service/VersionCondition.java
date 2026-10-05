package com.pokemon.bff.service;

import java.util.Collection;
import java.util.Set;

/**
 * What a write expects of the record's current version (optimistic concurrency): any version, as long as the record
 * exists, or one of the given versions. A write without a condition is passed {@code null}.
 * <p>
 * The versions are the opaque tokens of {@link com.pokemon.bff.persistence.entity.PokemonEntity#versionTag()}; how a
 * client sends them (an HTTP {@code If-Match} header) is the API layer's business.
 */
public record VersionCondition(boolean anyVersion, Set<String> versions) {

    public VersionCondition {
        versions = versions == null ? Set.of() : Set.copyOf(versions);
    }

    /** Any current version: the record must exist. */
    public static VersionCondition any() {
        return new VersionCondition(true, Set.of());
    }

    /** One of these versions; an empty collection can never be satisfied. */
    public static VersionCondition oneOf(Collection<String> versions) {
        return new VersionCondition(false, Set.copyOf(versions));
    }

    public boolean isSatisfiedBy(String currentVersion) {
        return anyVersion || versions.contains(currentVersion);
    }
}
