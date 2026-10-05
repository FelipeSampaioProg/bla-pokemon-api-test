package com.pokemon.bff;

import com.pokemon.bff.config.FeignConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PokemonApiBffApplicationTest {

    @Test
    void applicationClassIsProperlyAnnotated() {
        assertTrue(PokemonApiBffApplication.class.isAnnotationPresent(SpringBootApplication.class));
        assertTrue(FeignConfig.class.isAnnotationPresent(EnableFeignClients.class));
    }
}
