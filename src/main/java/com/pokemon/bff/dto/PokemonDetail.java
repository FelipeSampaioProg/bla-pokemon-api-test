package com.pokemon.bff.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pokemon.bff.persistence.entity.PokemonEntity;

import java.util.List;

/**
 * Detail view of a Pokemon. Holds everything the list entry ({@link PokemonItem}) shows plus the detail-only data,
 * so opening a card never shows less than the card itself.
 *
 * @param species              the species name, which is how this Pokemon appears in {@code evolutionLineage}
 *                             (e.g. {@code lycanroc} for {@code lycanroc-midday}).
 * @param descriptionAvailable {@code false} when the species data (description, category) could not be loaded; a
 *                             {@code null} description with this flag {@code true} means PokeAPI has no English text.
 * @param evolutionAvailable   {@code false} when the evolution chain could not be loaded from PokeAPI; the lineage
 *                             then holds only this Pokemon, and the rest of the detail is still valid.
 * @param retryAfterSeconds    present only when part of the detail is missing because PokeAPI is rate limiting us:
 *                             the seconds after which asking again should bring it back (also sent as the
 *                             {@code Retry-After} header). Same meaning as in {@link PokemonPage}.
 * @param metadata             proprietary fields added locally (localized name, region, classification tag), present
 *                             only when set. The PokeAPI detail merges them from the local store by id.
 */
public record PokemonDetail(int id, String name, String species, String image, Double height, Double mass,
                            String category, List<Skill> skills, List<PokemonStat> stats,
                            String description, boolean descriptionAvailable,
                            EvolutionNode evolutionLineage, boolean evolutionAvailable,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Integer retryAfterSeconds,
                            @JsonInclude(JsonInclude.Include.NON_NULL) PokemonMetadata metadata) {

    public PokemonDetail {
        if (skills == null) {
            skills = List.of();
        }
        if (stats == null) {
            stats = List.of();
        }
    }

    /** Detail with no proprietary fields. */
    public PokemonDetail(int id, String name, String species, String image, Double height, Double mass,
            String category, List<Skill> skills, List<PokemonStat> stats, String description,
            boolean descriptionAvailable, EvolutionNode evolutionLineage, boolean evolutionAvailable,
            Integer retryAfterSeconds) {
        this(id, name, species, image, height, mass, category, skills, stats, description, descriptionAvailable,
                evolutionLineage, evolutionAvailable, retryAfterSeconds, null);
    }

    /** The same detail with the given proprietary fields. */
    public PokemonDetail withMetadata(PokemonMetadata localMetadata) {
        return new PokemonDetail(id, name, species, image, height, mass, category, skills, stats, description,
                descriptionAvailable, evolutionLineage, evolutionAvailable, retryAfterSeconds, localMetadata);
    }

    /** Detail that was not rate limited. */
    public PokemonDetail(int id, String name, String species, String image, Double height, Double mass,
            String category, List<Skill> skills, List<PokemonStat> stats, String description,
            boolean descriptionAvailable, EvolutionNode evolutionLineage, boolean evolutionAvailable) {
        this(id, name, species, image, height, mass, category, skills, stats, description, descriptionAvailable,
                evolutionLineage, evolutionAvailable, null);
    }

    /** Detail with no category or skills and everything loaded. */
    public PokemonDetail(int id, String name, String image, Double height, Double mass,
            List<PokemonStat> stats, String description, EvolutionNode evolutionLineage) {
        this(id, name, image, height, mass, stats, description, evolutionLineage, true);
    }

    /** Detail with no category or skills. */
    public PokemonDetail(int id, String name, String image, Double height, Double mass,
            List<PokemonStat> stats, String description, EvolutionNode evolutionLineage,
            boolean evolutionAvailable) {
        this(id, name, name, image, height, mass, null, List.of(), stats, description, true,
                evolutionLineage, evolutionAvailable);
    }

    /**
     * Representation of a record of the local store, the same for its reads and its writes. Local records have no
     * evolution data, so the lineage contains only the Pokemon itself. Must be called inside the transaction that
     * loaded the entity, because stats and skills are lazy.
     */
    public static PokemonDetail local(PokemonEntity entity) {
        var stats = entity.getStats() == null ? List.<PokemonStat>of() : entity.getStats().stream()
                .map(stat -> new PokemonStat(stat.getName(), stat.getValue()))
                .toList();
        var skills = entity.getSkills() == null ? List.<Skill>of() : entity.getSkills().stream()
                .map(skill -> new Skill(skill.getName(), skill.getUrl()))
                .toList();
        return new PokemonDetail(entity.getId(), entity.getName(), entity.getName(), entity.getImageUrl(),
                entity.getHeight(), entity.getWeight(), entity.getCategory(), skills, stats,
                entity.getDescription(), true, new EvolutionNode(entity.getName(), List.of()), true, null,
                PokemonMetadata.from(entity.getMetadata()));
    }

    /** True when part of the detail could not be loaded from PokeAPI. */
    public boolean partial() {
        return !evolutionAvailable || !descriptionAvailable;
    }
}
