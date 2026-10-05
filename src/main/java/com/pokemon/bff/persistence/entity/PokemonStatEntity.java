package com.pokemon.bff.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pokemon_stat")
@Getter
@Setter
@NoArgsConstructor
public class PokemonStatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pokemon_id", nullable = false)
    private PokemonEntity pokemon;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer value;

    public PokemonStatEntity(PokemonEntity pokemon, String name, Integer value) {
        this.pokemon = pokemon;
        this.name = name;
        this.value = value;
    }
}
