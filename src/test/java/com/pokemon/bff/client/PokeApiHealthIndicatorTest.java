package com.pokemon.bff.client;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokeApiHealthIndicatorTest {
    private static final Request REQUEST = Request.create(Request.HttpMethod.GET, "https://pokeapi.co/api/v2/pokemon",
            Map.of(), null, StandardCharsets.UTF_8, null);

    private static FeignException httpError(int status) {
        return FeignException.errorStatus("PokemonClient#findAll",
                Response.builder().status(status).reason("x").request(REQUEST).headers(Map.of()).build());
    }

    @Test
    void shouldBeUnknownBeforeTheFirstCall() {
        var health = new PokeApiHealthIndicator(new PokeApiStatus()).health();

        assertEquals(Status.UNKNOWN, health.getStatus());
    }

    @Test
    void shouldBeUpAfterASuccessfulCall() {
        var status = new PokeApiStatus();
        status.recordSuccess();

        var health = new PokeApiHealthIndicator(status).health();

        assertEquals(Status.UP, health.getStatus());
        assertTrue(health.getDetails().containsKey("lastSuccess"));
    }

    @Test
    void shouldBeDownWhenTheLatestCallFailedAndRecoverAfterASuccess() {
        var clock = new MutableClock(Instant.parse("2026-10-04T10:00:00Z"));
        var status = new PokeApiStatus(clock);
        var indicator = new PokeApiHealthIndicator(status);

        status.recordSuccess();
        clock.now = clock.now.plusSeconds(1);
        status.record(new RetryableException(-1, "Read timed out", Request.HttpMethod.GET, (Long) null, REQUEST));
        var down = indicator.health();
        clock.now = clock.now.plusSeconds(1);
        status.recordSuccess();
        var up = indicator.health();

        assertEquals(Status.DOWN, down.getStatus());
        assertTrue(String.valueOf(down.getDetails().get("lastError")).contains("Read timed out"));
        assertEquals(Status.UP, up.getStatus());
    }

    @Test
    void shouldStayUpWithTheLimitWhenPokeApiIsRateLimiting() {
        var clock = new MutableClock(Instant.parse("2026-10-04T10:00:00Z"));
        var status = new PokeApiStatus(clock);
        var indicator = new PokeApiHealthIndicator(status);

        status.recordRateLimit(clock.now.plusSeconds(30));
        var limited = indicator.health();
        clock.now = clock.now.plusSeconds(31);
        var lifted = indicator.health();

        assertEquals(Status.UP, limited.getStatus());
        assertEquals("2026-10-04T10:00:30Z", limited.getDetails().get("rateLimitedUntil"));
        assertEquals(Status.UP, lifted.getStatus());
        assertFalse(lifted.getDetails().containsKey("rateLimitedUntil"));
    }

    @Test
    void shouldCountOnlyUpstreamProblemsAsFailures() {
        assertTrue(PokeApiStatus.isUpstreamFailure(httpError(503)));
        assertTrue(PokeApiStatus.isUpstreamFailure(new IllegalStateException("unreadable body")));
        assertTrue(PokeApiStatus.isUpstreamFailure(
                new RetryableException(-1, "Read timed out", Request.HttpMethod.GET, (Long) null, REQUEST)));
        // PokeAPI answered a rate limit: working, only limiting us
        assertFalse(PokeApiStatus.isUpstreamFailure(httpError(429)));
        assertFalse(PokeApiStatus.isUpstreamFailure(
                new RetryableException(429, "slow down", Request.HttpMethod.GET, (Long) null, REQUEST)));
        assertFalse(PokeApiStatus.isUpstreamFailure(new PokeApiRateLimitException(Instant.now(), true, null)));
        // PokeAPI answered: a missing Pokemon is not an outage
        assertFalse(PokeApiStatus.isUpstreamFailure(httpError(404)));
        assertFalse(PokeApiStatus.isUpstreamFailure(httpError(400)));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
