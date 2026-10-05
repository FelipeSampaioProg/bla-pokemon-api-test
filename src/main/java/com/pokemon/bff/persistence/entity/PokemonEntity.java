package com.pokemon.bff.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pokemon")
@Getter
@Setter
@NoArgsConstructor
public class PokemonEntity {

    @Id
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    private Double height;

    private Double weight;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    /** True once the record was created or edited through the API; PokeAPI sync must not overwrite it. */
    @Column(name = "locally_modified", nullable = false)
    private boolean locallyModified;

    /**
     * Optimistic lock. Makes the {@code locallyModified} protection atomic: an asynchronous PokeAPI sync that loaded
     * the row before a local edit fails on save instead of overwriting the edit.
     */
    @Version
    private Long version;

    @OneToMany(mappedBy = "pokemon", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PokemonStatEntity> stats = new ArrayList<>();

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "pokemon", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PokemonSkillEntity> skills = new ArrayList<>();

    @OneToOne(mappedBy = "pokemon", cascade = CascadeType.ALL, orphanRemoval = true)
    private PokemonMetadataEntity metadata;

    /**
     * Opaque token of this state of the record, the base of its HTTP {@code ETag}: the version plus the moment the
     * record was created or last replicated (the version alone restarts at 0 when a deleted record is created
     * again, and a token read before the deletion would then match the new record).
     */
    public String versionTag() {
        return (version == null ? 0 : version) + "-" + (syncedAt == null ? 0 : syncedAt.toEpochMilli());
    }

    public PokemonEntity(Integer id, String name, String imageUrl, Double height, Double weight,
            String description, Instant syncedAt) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.height = height;
        this.weight = weight;
        this.description = description;
        this.syncedAt = syncedAt;
    }
}
