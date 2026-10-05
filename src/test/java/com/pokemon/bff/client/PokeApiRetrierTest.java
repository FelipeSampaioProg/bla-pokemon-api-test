package com.pokemon.bff.client;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PokeApiRetrierTest {
    private static final Request REQUEST = Request.create(Request.HttpMethod.GET, "https://pokeapi.co/api/v2/pokemon",
            Map.of(), null, StandardCharsets.UTF_8, null);
    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");

    private final FakeClock clock = new FakeClock(T0);
    private final List<Duration> sleeps = new ArrayList<>();

    /** 3 attempts, 100 ms doubling up to 1 s, 6 s budget, 10 s default pause; sleeping just advances the clock. */
    private PokeApiRetrier retrier() {
        return new PokeApiRetrier(3, Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofSeconds(6),
                Duration.ofSeconds(10), clock, duration -> {
            sleeps.add(duration);
            clock.now = clock.now.plus(duration);
        });
    }

    private static RetryableException transientFailure(int status) {
        return new RetryableException(status, "upstream " + status, Request.HttpMethod.GET, (Long) null, REQUEST);
    }

    private static RetryableException rateLimited(Instant retryAfter) {
        return new RetryableException(429, "slow down", Request.HttpMethod.GET,
                retryAfter == null ? null : retryAfter.toEpochMilli(), REQUEST);
    }

    @Test
    void shouldRetryATransientFailureWithBackoffAndSucceed() {
        var calls = new AtomicInteger();

        String result = retrier().execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw transientFailure(503);
            }
            return "ok";
        }, true);

        assertEquals("ok", result);
        assertEquals(3, calls.get());
        assertEquals(List.of(Duration.ofMillis(100), Duration.ofMillis(200)), sleeps);
    }

    @Test
    void shouldGiveUpAfterMaxAttemptsKeepingTheLastFailure() {
        var calls = new AtomicInteger();

        var thrown = assertThrows(RetryableException.class, () -> retrier().execute(() -> {
            calls.incrementAndGet();
            throw transientFailure(502);
        }, true));

        assertEquals(502, thrown.status());
        assertEquals(3, calls.get());
    }

    @Test
    void shouldNotRetryWhenTheCallerDoesNotAllowIt() {
        var calls = new AtomicInteger();

        assertThrows(RetryableException.class, () -> retrier().execute(() -> {
            calls.incrementAndGet();
            throw transientFailure(-1);
        }, false));

        assertEquals(1, calls.get());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void shouldNotRetryBeyondTheTimeBudget() {
        var calls = new AtomicInteger();

        // each attempt takes 4 s (a slow timeout): a retry would end after the 6 s budget
        assertThrows(RetryableException.class, () -> retrier().execute(() -> {
            calls.incrementAndGet();
            clock.now = clock.now.plusSeconds(4);
            throw transientFailure(-1);
        }, true));

        assertEquals(1, calls.get());
    }

    @Test
    void shouldNeverRetryNonTransientFailures() {
        var calls = new AtomicInteger();
        var notFound = FeignException.errorStatus("PokemonClient#find",
                Response.builder().status(404).reason("x").request(REQUEST).headers(Map.of()).build());

        var thrown = assertThrows(PokeApiNotFoundException.class, () -> retrier().execute(() -> {
            calls.incrementAndGet();
            throw notFound;
        }, true));

        assertEquals(1, calls.get());
        // Reported by the client layer itself, so the layers above never see the HTTP library's exception.
        assertSame(notFound, thrown.getCause());
    }

    @Test
    void shouldPauseEveryCallAfterARateLimitWithoutReachingPokeApi() {
        var retrier = retrier();
        var calls = new AtomicInteger();

        var first = assertThrows(PokeApiRateLimitException.class, () -> retrier.execute(() -> {
            calls.incrementAndGet();
            throw rateLimited(T0.plusSeconds(30));
        }, false));
        var second = assertThrows(PokeApiRateLimitException.class, () -> retrier.execute(() -> {
            calls.incrementAndGet();
            return "never called";
        }, true));

        assertTrue(first.upstreamCalled());
        assertFalse(second.upstreamCalled());
        assertEquals(T0.plusSeconds(30), second.retryAfter());
        assertEquals(1, calls.get());
    }

    @Test
    void shouldResumeCallsOnceThePauseIsOver() {
        var retrier = retrier();
        assertThrows(PokeApiRateLimitException.class,
                () -> retrier.execute(() -> { throw rateLimited(T0.plusSeconds(30)); }, false));

        clock.now = T0.plusSeconds(31);

        assertEquals("ok", retrier.execute(() -> "ok", false));
    }

    @Test
    void shouldUseTheDefaultPauseWhenPokeApiGivesNoRetryAfter() {
        var thrown = assertThrows(PokeApiRateLimitException.class,
                () -> retrier().execute(() -> { throw rateLimited(null); }, true));

        // 10 s default pause does not fit the 6 s budget: no wait, fail at once
        assertEquals(T0.plusSeconds(10), thrown.retryAfter());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void shouldWaitForAShortRateLimitWhenItFitsTheBudget() {
        var calls = new AtomicInteger();

        String result = retrier().execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw rateLimited(T0.plusSeconds(2));
            }
            return "ok";
        }, true);

        assertEquals("ok", result);
        assertEquals(List.of(Duration.ofSeconds(2)), sleeps);
    }

    /** A call that fails twice, 1 s per attempt, then succeeds in 1 s: ends 3.3 s after it starts. */
    private String flakyCallTaking3Seconds(PokeApiRetrier retrier, AtomicInteger calls) {
        var attempts = new AtomicInteger();
        return retrier.execute(() -> {
            calls.incrementAndGet();
            clock.now = clock.now.plusMillis(1_000);
            if (attempts.incrementAndGet() < 3) {
                throw transientFailure(503);
            }
            return "ok";
        }, true);
    }

    @Test
    void shouldShareOneBudgetAcrossTheCallsOfARequest() {
        var retrier = retrier();
        var calls = new AtomicInteger();

        retrier.withinBudget(() -> {
            flakyCallTaking3Seconds(retrier, calls);
            // 3.3 s of the 6 s request budget are spent: a 1.5 s attempt plus a retry no longer fits
            assertThrows(RetryableException.class, () -> retrier.execute(() -> {
                calls.incrementAndGet();
                clock.now = clock.now.plusMillis(1_500);
                throw transientFailure(503);
            }, true));
            return null;
        });

        assertEquals(4, calls.get());
    }

    @Test
    void shouldGiveEachCallItsOwnBudgetOutsideARequestBudget() {
        var retrier = retrier();
        var calls = new AtomicInteger();
        flakyCallTaking3Seconds(retrier, calls);

        // without a request budget the second call starts a fresh 6 s budget and may retry
        var second = new AtomicInteger();
        String result = retrier.execute(() -> {
            clock.now = clock.now.plusMillis(1_500);
            if (second.incrementAndGet() < 2) {
                throw transientFailure(503);
            }
            return "ok";
        }, true);

        assertEquals("ok", result);
        assertEquals(2, second.get());
    }

    @Test
    void shouldNeverRetryOrPauseWithTheNonePolicy() {
        var calls = new AtomicInteger();
        var none = PokeApiRetrier.none();

        assertThrows(RetryableException.class, () -> none.execute(() -> {
            calls.incrementAndGet();
            throw transientFailure(503);
        }, true));

        assertEquals(1, calls.get());
    }

    private static final class FakeClock extends Clock {
        private Instant now;

        private FakeClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
