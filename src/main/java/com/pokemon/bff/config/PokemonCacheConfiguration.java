package com.pokemon.bff.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
@EnableConfigurationProperties(PokemonCacheProperties.class)
public class PokemonCacheConfiguration {

    @Bean
    public CacheManager cacheManager(PokemonCacheProperties properties,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider) {
        return switch (properties.getProvider()) {
            case NONE -> new NoOpCacheManager();
            case CAFFEINE -> caffeineCacheManager(properties);
            case REDIS -> redisCacheManager(properties, redisConnectionFactoryProvider.getIfAvailable());
        };
    }

    CacheManager caffeineCacheManager(PokemonCacheProperties properties) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                buildCaffeineCache(PokemonCacheNames.POKEMON_PAGE, properties.getPage()),
                buildCaffeineCache(PokemonCacheNames.POKEAPI_POKEMON, properties.getResource()),
                buildCaffeineCache(PokemonCacheNames.POKEAPI_SPECIES, properties.getResource()),
                buildCaffeineCache(PokemonCacheNames.POKEAPI_EVOLUTION, properties.getResource()),
                buildCaffeineCache(PokemonCacheNames.POKEMON_SYNC, properties.getResource())));
        return cacheManager;
    }

    /**
     * JSON values that remember their type, so a cached PokeAPI resource is read back as the same record. Jackson 3
     * version of the serializer (the Jackson 2 one is deprecated for removal). Only this application's types, the
     * collections they hold and plain Java values may be read back: an entry written by someone else into the shared
     * Redis cannot make the application instantiate an arbitrary class.
     */
    static RedisSerializer<Object> redisValueSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.pokemon.bff.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.lang.")
                        .build())
                .build();
    }

    CacheManager redisCacheManager(PokemonCacheProperties properties, RedisConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
            throw new IllegalStateException("Redis cache provider requires a RedisConnectionFactory");
        }

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisValueSerializer()))
                .prefixCacheNameWith("bla-pokemon-technical-test::");

        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
                PokemonCacheNames.POKEMON_PAGE, defaults.entryTtl(properties.getPage().getTtl()),
                PokemonCacheNames.POKEAPI_POKEMON, defaults.entryTtl(properties.getResource().getTtl()),
                PokemonCacheNames.POKEAPI_SPECIES, defaults.entryTtl(properties.getResource().getTtl()),
                PokemonCacheNames.POKEAPI_EVOLUTION, defaults.entryTtl(properties.getResource().getTtl()),
                PokemonCacheNames.POKEMON_SYNC, defaults.entryTtl(properties.getResource().getTtl()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    private CaffeineCache buildCaffeineCache(String name, PokemonCacheProperties.CacheSpec spec) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(spec.getTtl())
                .maximumSize(spec.getMaximumSize())
                .build());
    }
}
