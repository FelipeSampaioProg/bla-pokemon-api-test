package com.pokemon.bff.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Proprietary extension fields for a Pokemon.
 * These fields are not sourced from PokeAPI and must be populated manually.
 * Use cases: localized nomenclature, geographical metadata, internal classification tags.
 */
@Entity
@Table(name = "pokemon_metadata")
@Getter
@Setter
@NoArgsConstructor
public class PokemonMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pokemon_id", nullable = false, unique = true)
    private PokemonEntity pokemon;

    /** Localized name for regional nomenclature (e.g. Spanish, Japanese). */
    @Column(name = "localized_name")
    private String localizedName;

    /** Geographical region or habitat metadata (e.g. "Kanto", "Johto"). */
    private String region;

    /** Internal classification tag for business logic (e.g. "STARTER", "LEGENDARY"). */
    @Column(name = "classification_tag")
    private String classificationTag;
}
