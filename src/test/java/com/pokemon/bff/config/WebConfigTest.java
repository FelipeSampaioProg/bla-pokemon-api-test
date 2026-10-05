package com.pokemon.bff.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebConfigTest {

    @Test
    void shouldAllowEveryVerbOfTheApiAndExposeItsHeaders() {
        var registry = new CorsRegistry() {
            Map<String, CorsConfiguration> configurations() {
                return getCorsConfigurations();
            }
        };
        new WebConfig().corsConfigurer().addCorsMappings(registry);

        var api = registry.configurations().get("/api/**");
        assertTrue(api.getAllowedMethods().containsAll(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE")));
        assertTrue(api.getExposedHeaders().containsAll(java.util.List.of("ETag", "Location", "Retry-After")));
    }
}
