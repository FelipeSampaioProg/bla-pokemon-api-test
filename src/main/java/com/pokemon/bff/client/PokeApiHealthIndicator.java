package com.pokemon.bff.client;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Shows the PokeAPI integration in {@code /actuator/health} as the {@code pokeApi} component.
 * <p>
 * UNKNOWN until the first call, DOWN when the latest call failed (timeout, 5xx), UP otherwise. A rate limit (429) keeps
 * it UP with a {@code rateLimitedUntil} detail: PokeAPI is working, only limiting us. It is part of
 * the overall health but not of the liveness/readiness probes, so an upstream outage never restarts or
 * unschedules this service: it keeps serving cached data and local records.
 */
@Component
public class PokeApiHealthIndicator implements HealthIndicator {
    private final PokeApiStatus status;

    public PokeApiHealthIndicator(PokeApiStatus status) {
        this.status = status;
    }

    @Override
    public Health health() {
        var lastSuccess = status.lastSuccess();
        var lastFailure = status.lastFailure();
        if (lastSuccess == null && lastFailure == null) {
            return Health.unknown().withDetail("reason", "No PokeAPI call made yet").build();
        }
        Health.Builder builder = lastFailure != null && (lastSuccess == null || lastFailure.isAfter(lastSuccess))
                ? Health.down().withDetail("lastError", status.lastError())
                : Health.up();
        if (lastSuccess != null) {
            builder.withDetail("lastSuccess", lastSuccess.toString());
        }
        if (lastFailure != null) {
            builder.withDetail("lastFailure", lastFailure.toString());
        }
        var rateLimitedUntil = status.rateLimitedUntil();
        if (rateLimitedUntil != null) {
            builder.withDetail("rateLimitedUntil", rateLimitedUntil.toString());
        }
        return builder.build();
    }
}
