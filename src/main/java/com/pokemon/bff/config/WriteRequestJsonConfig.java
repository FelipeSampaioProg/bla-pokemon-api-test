package com.pokemon.bff.config;

import com.pokemon.bff.dto.PokemonCreateRequest;
import com.pokemon.bff.dto.PokemonReplaceRequest;
import com.pokemon.bff.dto.PokemonUpdateRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

import java.util.List;

/**
 * Strict JSON reading for the bodies of the local writes ({@code POST}, {@code PUT}, {@code PATCH}).
 * <p>
 * The application-wide mapper is lenient, and must stay so for PokeAPI's responses (which carry many fields we do
 * not map). For a write, though, leniency hides mistakes: a misspelt field ({@code "heigth"}) was ignored and the
 * request answered 200 without changing anything, {@code 1.7} was stored as {@code 1}, and {@code "name": 123} as
 * {@code "123"}. Only these request types are read with the strict mapper; everything else is unchanged.
 */
@Configuration
public class WriteRequestJsonConfig implements WebMvcConfigurer {
    static final List<Class<?>> WRITE_REQUESTS =
            List.of(PokemonCreateRequest.class, PokemonReplaceRequest.class, PokemonUpdateRequest.class);

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConverters(converter -> {
            if (converter instanceof JacksonJsonHttpMessageConverter jackson) {
                JsonMapper strict = strict(jackson.getMapper());
                for (Class<?> type : WRITE_REQUESTS) {
                    jackson.registerMappersForType(type, mappers -> mappers.put(MediaType.APPLICATION_JSON, strict));
                }
            }
        });
    }

    static JsonMapper strict(JsonMapper base) {
        return base.rebuild()
                // A field that does not exist is a mistake, not something to skip.
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // {"name": "a", "name": "b"}: which one did the client mean?
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                // No silent conversions: 1.7 is not an integer, 123 is not a text, "5" is not a number.
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Float, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .build();
    }
}
