package com.pokemon.bff.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class PokemonAsyncConfiguration implements AsyncConfigurer {
    public static final String POKEMON_SYNC_EXECUTOR = "pokemonSyncExecutor";
    private static final Logger log = LoggerFactory.getLogger(PokemonAsyncConfiguration.class);

    /**
     * Runs the local-store synchronisation off the request thread. The queue is bounded: when it is full the
     * submission is rejected and the caller just skips that sync, so reads are never slowed down by the database.
     */
    @Bean(name = POKEMON_SYNC_EXECUTOR)
    public Executor pokemonSyncExecutor(@Value("${pokemon.sync.threads:2}") int threads,
            @Value("${pokemon.sync.queue-capacity:1000}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("pokemon-sync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (e, method, params) -> log.warn("Async {} failed: {}", method.getName(), e.getMessage());
    }
}
