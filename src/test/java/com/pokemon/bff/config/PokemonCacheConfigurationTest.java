package com.pokemon.bff.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Iterator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PokemonCacheConfigurationTest {
    private final PokemonCacheConfiguration configuration = new PokemonCacheConfiguration();

    @Test
    void shouldCreateNoOpCacheManagerWhenProviderIsNone() {
        PokemonCacheProperties properties = new PokemonCacheProperties();
        properties.setProvider(PokemonCacheProperties.Provider.NONE);

        CacheManager cacheManager = configuration.cacheManager(properties, objectProvider(null));

        assertInstanceOf(NoOpCacheManager.class, cacheManager);
    }

    @Test
    void shouldCreateCaffeineCacheManagerWhenProviderIsCaffeine() {
        PokemonCacheProperties properties = new PokemonCacheProperties();
        properties.setProvider(PokemonCacheProperties.Provider.CAFFEINE);

        CacheManager cacheManager = configuration.cacheManager(properties, objectProvider(null));

        assertInstanceOf(SimpleCacheManager.class, cacheManager);
        ((SimpleCacheManager) cacheManager).afterPropertiesSet();
        assertNotNull(cacheManager.getCache(PokemonCacheNames.POKEMON_PAGE));
        assertNotNull(cacheManager.getCache(PokemonCacheNames.POKEAPI_POKEMON));
        assertNotNull(cacheManager.getCache(PokemonCacheNames.POKEAPI_SPECIES));
        assertNotNull(cacheManager.getCache(PokemonCacheNames.POKEAPI_EVOLUTION));
        assertNotNull(cacheManager.getCache(PokemonCacheNames.POKEMON_SYNC));
    }

    @Test
    void shouldCreateRedisCacheManagerWhenProviderIsRedis() {
        PokemonCacheProperties properties = new PokemonCacheProperties();
        properties.setProvider(PokemonCacheProperties.Provider.REDIS);

        CacheManager cacheManager = configuration.cacheManager(properties, objectProvider(mock(RedisConnectionFactory.class)));

        assertInstanceOf(RedisCacheManager.class, cacheManager);
    }

    @Test
    void shouldFailWhenRedisProviderHasNoConnectionFactory() {
        PokemonCacheProperties properties = new PokemonCacheProperties();
        properties.setProvider(PokemonCacheProperties.Provider.REDIS);

        assertThrows(IllegalStateException.class,
                () -> configuration.cacheManager(properties, objectProvider(null)));
    }

    private static <T> ObjectProvider<T> objectProvider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public Iterator<T> iterator() {
                return stream().iterator();
            }

            @Override
            public Stream<T> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }

            @Override
            public Stream<T> orderedStream() {
                return stream();
            }
        };
    }

    // ---- Redis values: Jackson 3 serializer with a type validator ----

    private static final tools.jackson.databind.json.JsonMapper FEIGN_LIKE = tools.jackson.databind.json.JsonMapper.builder()
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Test
    void shouldReadEveryCachedPokeApiResourceBackAsTheSameRecord() {
        var serializer = PokemonCacheConfiguration.redisValueSerializer();
        // Decoded from PokeAPI's JSON the way the Feign client does (lists come back as ArrayList).
        var values = java.util.List.<Object>of(
                FEIGN_LIKE.readValue("""
                        {"count": 1351, "results": [{"name": "bulbasaur", "url": "https://pokeapi.co/api/v2/pokemon/1/"}]}""",
                        com.pokemon.bff.client.dto.PokemonListResponse.class),
                FEIGN_LIKE.readValue("""
                        {"id": 25, "name": "pikachu", "height": 4, "weight": 60,
                         "sprites": {"front_default": "https://img.example/25.png"},
                         "abilities": [{"ability": {"name": "static", "url": "https://pokeapi.co/api/v2/ability/9/"}}],
                         "stats": [{"base_stat": 35, "stat": {"name": "hp", "url": "u"}}],
                         "species": {"name": "pikachu", "url": "https://pokeapi.co/api/v2/pokemon-species/25/"}}""",
                        com.pokemon.bff.client.dto.PokemonDetailsResponse.class),
                FEIGN_LIKE.readValue("""
                        {"genera": [{"genus": "Mouse Pokémon", "language": {"name": "en"}}],
                         "flavor_text_entries": [{"flavor_text": "Electric.", "language": {"name": "en"}}],
                         "evolution_chain": {"url": "https://pokeapi.co/api/v2/evolution-chain/10/"},
                         "varieties": [{"is_default": true, "pokemon": {"name": "pikachu", "url": "u"}}]}""",
                        com.pokemon.bff.client.dto.PokemonSpeciesResponse.class),
                FEIGN_LIKE.readValue("""
                        {"chain": {"species": {"name": "pichu", "url": "u"},
                                   "evolves_to": [{"species": {"name": "pikachu", "url": "u"}, "evolves_to": []}]}}""",
                        com.pokemon.bff.client.dto.EvolutionChainResponse.class),
                Boolean.TRUE);

        for (Object value : values) {
            assertEquals(value, serializer.deserialize(serializer.serialize(value)), value.getClass().getSimpleName());
        }
    }

    @Test
    void shouldRefuseToReadATypeOutsideTheApplication() {
        var serializer = PokemonCacheConfiguration.redisValueSerializer();
        // An entry written into the shared Redis by someone else, naming a class of their choice.
        String genuine = new String(serializer.serialize(
                new com.pokemon.bff.client.dto.PokemonReference("pikachu", "u")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(genuine.contains("com.pokemon.bff.client.dto.PokemonReference"), genuine);
        byte[] forged = genuine.replace("com.pokemon.bff.client.dto.PokemonReference", "java.net.Socket")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThrows(org.springframework.data.redis.serializer.SerializationException.class,
                () -> serializer.deserialize(forged));
    }
}

