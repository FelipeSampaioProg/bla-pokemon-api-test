package com.pokemon.bff.dto;

import com.pokemon.bff.persistence.entity.PokemonMetadataEntity;

/**
 * Proprietary fields added locally on top of the replicated PokeAPI data (they do not exist in PokeAPI).
 */
public record PokemonMetadata(String localizedName, String region, String classificationTag) {

    /** {@code null} when the Pokemon has no proprietary field set, so responses omit the block. */
    public static PokemonMetadata from(PokemonMetadataEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getLocalizedName() == null && entity.getRegion() == null && entity.getClassificationTag() == null) {
            return null;
        }
        return new PokemonMetadata(entity.getLocalizedName(), entity.getRegion(), entity.getClassificationTag());
    }
}
