package com.pokemon.bff.config;

import com.pokemon.bff.client.PokeApiErrorDecoder;
import com.pokemon.bff.client.PokemonClient;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign clients are enabled here instead of on the application class so that
 * sliced tests (e.g. {@code @WebMvcTest}) do not try to build the PokeAPI client.
 */
@Configuration
@EnableFeignClients(basePackageClasses = PokemonClient.class)
public class FeignConfig {

    /**
     * Feign itself never retries: a blanket policy would also retry each entry of a page, multiplying latency and
     * load on PokeAPI. {@link com.pokemon.bff.client.PokeApiRetrier} retries only where the caller allows it.
     */
    @Bean
    public Retryer pokeApiRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public ErrorDecoder pokeApiErrorDecoder() {
        return new PokeApiErrorDecoder();
    }
}
