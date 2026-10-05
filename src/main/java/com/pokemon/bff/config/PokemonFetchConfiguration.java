package com.pokemon.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class PokemonFetchConfiguration {
    public static final String POKEMON_FETCH_EXECUTOR = "pokemonFetchExecutor";

    /**
     * Bounded pool used to load the Pokemon of a page in parallel without flooding the upstream PokeAPI.
     */
    @Bean(name = POKEMON_FETCH_EXECUTOR)
    public Executor pokemonFetchExecutor(@Value("${pokemon.fetch.parallelism:8}") int parallelism) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setThreadNamePrefix("pokeapi-fetch-");
        executor.initialize();
        return executor;
    }
}
