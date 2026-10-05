package com.pokemon.bff.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pokemonApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pokemon API BFF")
                        .version("1.0.0")
                        .description("API for querying and managing local Pokémon data."));
    }
}
