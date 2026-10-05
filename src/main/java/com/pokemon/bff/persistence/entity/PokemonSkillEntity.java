package com.pokemon.bff.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pokemon_skill")
@Getter
@Setter
@NoArgsConstructor
public class PokemonSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pokemon_id", nullable = false)
    private PokemonEntity pokemon;

    @Column(nullable = false)
    private String name;

    @Column(length = 512)
    private String url;

    public PokemonSkillEntity(PokemonEntity pokemon, String name, String url) {
        this.pokemon = pokemon;
        this.name = name;
        this.url = url;
    }
}
