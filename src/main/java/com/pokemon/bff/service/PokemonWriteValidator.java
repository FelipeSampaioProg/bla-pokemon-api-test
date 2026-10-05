package com.pokemon.bff.service;

import com.pokemon.bff.dto.PokemonStat;
import com.pokemon.bff.dto.Skill;
import com.pokemon.bff.exception.InvalidFieldException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Field rules shared by the local writes ({@code POST}, {@code PUT}, {@code PATCH}). Every value that reaches the
 * database is checked here first, so a bad value is a 400 that names the field, never a database error (a value
 * longer than its column used to surface as a misleading 409 "duplicate id or name").
 * <p>
 * Text limits are the column sizes of {@code V1__create_pokemon_tables.sql}.
 */
final class PokemonWriteValidator {
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_URL_LENGTH = 512;
    static final int MAX_CATEGORY_LENGTH = 100;
    static final int MAX_LOCALIZED_NAME_LENGTH = 200;
    static final int MAX_PROPRIETARY_TAG_LENGTH = 100;
    /** The column is TEXT (64 KB); 5,000 characters fit it in any encoding and are far above PokeAPI's texts. */
    static final int MAX_DESCRIPTION_LENGTH = 5000;
    /** PokeAPI has 6 base stats and up to 3 abilities per Pokemon; the limits leave room for local data. */
    static final int MAX_STATS = 20;
    static final int MAX_SKILLS = 20;
    /** Highest base stat in the games (Blissey's HP); the detail panel draws the bars against it. */
    static final int MAX_STAT_VALUE = 255;
    /** Generous ceilings over the largest Pokemon (Eternamax Eternatus: 100 m, Celesteela: 999.9 kg). */
    static final double MAX_HEIGHT = 1000;
    static final double MAX_MASS = 10000;

    /**
     * PokeAPI's own name format ({@code mr-mime}, {@code porygon-z}): the name is used in URLs
     * ({@code /local-pokemons/{name}}), so spaces, slashes and other symbols would make the record unreachable.
     */
    private static final Pattern NAME_FORMAT = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private PokemonWriteValidator() {
    }

    static void id(Integer id) {
        if (id == null || id <= 0) {
            throw new InvalidFieldException("id", "Pokemon id must be positive");
        }
    }

    /** The id is taken from the path; one sent in the body as well must be the same, or the request is ambiguous. */
    static void bodyIdMatchesPath(Integer bodyId, Integer pathId) {
        if (bodyId != null && !bodyId.equals(pathId)) {
            throw new InvalidFieldException("id", "Pokemon id in the body (" + bodyId
                    + ") does not match the id in the path (" + pathId + ")");
        }
    }

    /** Checks a name that will be stored. {@code required}: whether a missing name is an error. */
    static void name(String name, boolean required) {
        if (name == null && !required) {
            return;
        }
        if (name == null || name.isBlank()) {
            throw new InvalidFieldException("name", "Pokemon name must not be blank");
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        maxLength(normalized, MAX_NAME_LENGTH, "name");
        if (!NAME_FORMAT.matcher(normalized).matches()) {
            throw new InvalidFieldException("name", "Pokemon name may only contain letters, digits and single "
                    + "hyphens between them (like mr-mime)");
        }
        if (normalized.chars().allMatch(Character::isDigit)) {
            // GET /local-pokemons/{nameOrId} reads digits as an id: a numeric name could never be looked up.
            throw new InvalidFieldException("name", "Pokemon name must contain a letter; a numeric name would be "
                    + "read as an id");
        }
    }

    /** A blank image clears it; anything else must be an absolute http(s) URL, since clients render it. */
    static void image(String image) {
        url(image, "image");
    }

    static void description(String description) {
        if (description != null) {
            maxLength(description.trim(), MAX_DESCRIPTION_LENGTH, "description");
        }
    }

    static void category(String category) {
        if (category != null) {
            maxLength(category.trim(), MAX_CATEGORY_LENGTH, "category");
        }
    }

    static void height(Double height) {
        measure(height, MAX_HEIGHT, "height", "m");
    }

    static void mass(Double mass) {
        measure(mass, MAX_MASS, "mass", "kg");
    }

    static void stats(List<PokemonStat> stats) {
        if (stats == null) {
            return;
        }
        if (stats.size() > MAX_STATS) {
            throw new InvalidFieldException("stats", "A Pokemon may have at most " + MAX_STATS + " stats");
        }
        for (int i = 0; i < stats.size(); i++) {
            PokemonStat stat = stats.get(i);
            String field = "stats[" + i + "]";
            if (stat == null) {
                throw new InvalidFieldException(field, field + " must not be null");
            }
            childName(stat.name(), field + ".name", "Stat");
            if (stat.value() < 0 || stat.value() > MAX_STAT_VALUE) {
                throw new InvalidFieldException(field + ".value",
                        "Stat values must be between 0 and " + MAX_STAT_VALUE);
            }
        }
        noDuplicates(stats, PokemonStat::name, "stats", "stat");
    }

    static void skills(List<Skill> skills) {
        if (skills == null) {
            return;
        }
        if (skills.size() > MAX_SKILLS) {
            throw new InvalidFieldException("skills", "A Pokemon may have at most " + MAX_SKILLS + " skills");
        }
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            String field = "skills[" + i + "]";
            if (skill == null) {
                throw new InvalidFieldException(field, field + " must not be null");
            }
            childName(skill.name(), field + ".name", "Skill");
            url(skill.url(), field + ".url");
        }
        noDuplicates(skills, Skill::name, "skills", "skill");
    }

    static void proprietaryFields(String localizedName, String region, String classificationTag) {
        if (localizedName != null) {
            maxLength(localizedName.trim(), MAX_LOCALIZED_NAME_LENGTH, "localizedName");
        }
        if (region != null) {
            maxLength(region.trim(), MAX_PROPRIETARY_TAG_LENGTH, "region");
        }
        if (classificationTag != null) {
            maxLength(classificationTag.trim(), MAX_PROPRIETARY_TAG_LENGTH, "classificationTag");
        }
    }

    private static void childName(String name, String field, String kind) {
        if (name == null || name.isBlank()) {
            throw new InvalidFieldException(field, kind + " names must not be blank");
        }
        maxLength(name.trim(), MAX_NAME_LENGTH, field);
    }

    /** Names are stored trimmed and in lower case, so {@code HP} and {@code hp} are the same stat. */
    private static <T> void noDuplicates(List<T> values, Function<T, String> name, String field, String kind) {
        var seen = new HashSet<String>();
        for (T value : values) {
            String normalized = name.apply(value).trim().toLowerCase(Locale.ROOT);
            if (!seen.add(normalized)) {
                throw new InvalidFieldException(field, "Duplicate " + kind + " '" + normalized + "'");
            }
        }
    }

    private static void url(String value, String field) {
        if (value == null || value.isBlank()) {
            return;
        }
        String url = value.trim();
        maxLength(url, MAX_URL_LENGTH, field);
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) {
                return;
            }
        } catch (URISyntaxException e) {
            // reported below
        }
        throw new InvalidFieldException(field, "Pokemon " + field + " must be an absolute http or https URL");
    }

    private static void measure(Double value, double max, String field, String unit) {
        if (value != null && (!Double.isFinite(value) || value < 0 || value > max)) {
            throw new InvalidFieldException(field, "Pokemon " + field + " must be between 0 and "
                    + (long) max + " " + unit);
        }
    }

    private static void maxLength(String value, int max, String field) {
        if (value.length() > max) {
            throw new InvalidFieldException(field, "Pokemon " + field + " must have at most " + max + " characters");
        }
    }
}
