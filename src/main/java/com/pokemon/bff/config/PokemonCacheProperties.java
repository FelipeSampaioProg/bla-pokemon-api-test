package com.pokemon.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pokemon.cache")
public class PokemonCacheProperties {
    private Provider provider = Provider.CAFFEINE;
    private final CacheSpec page = new CacheSpec(Duration.ofMinutes(5), 128);
    /** PokeAPI resources (pokemon, species, evolution chain) and the sync marks. */
    private final CacheSpec resource = new CacheSpec(Duration.ofMinutes(15), 4096);

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public CacheSpec getPage() {
        return page;
    }

    public CacheSpec getResource() {
        return resource;
    }

    public enum Provider {
        REDIS,
        CAFFEINE,
        NONE
    }

    public static class CacheSpec {
        private Duration ttl;
        private long maximumSize;

        public CacheSpec() {
        }

        public CacheSpec(Duration ttl, long maximumSize) {
            this.ttl = ttl;
            this.maximumSize = maximumSize;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }
    }
}
