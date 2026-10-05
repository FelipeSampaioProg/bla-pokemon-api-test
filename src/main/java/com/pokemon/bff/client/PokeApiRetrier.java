package com.pokemon.bff.client;

import feign.FeignException;
import feign.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Retry policy for PokeAPI calls, decided by the caller rather than applied to every request.
 * <ul>
 *   <li><b>Who retries:</b> only calls that answer a request directly (the page's reference list, the detail view).
 *   The entries of a page are loaded without retries: they already degrade to {@code available: false}, and
 *   retrying up to 100 entries in parallel would multiply both the latency and the load on PokeAPI.</li>
 *   <li><b>Time budget:</b> a retry is only made if the wait plus another attempt as long as the last one still fits
 *   in {@code pokeapi.retry.budget}. Quick failures (an immediate 503) are retried; a call that already timed out is
 *   not, so retrying never multiplies a timeout. The budget is counted from the first attempt of a call, or, inside
 *   {@link #withinBudget}, shared by every call of the same request (the detail view makes up to four).</li>
 *   <li><b>Rate limit:</b> a 429 pauses <em>every</em> PokeAPI call until its {@code Retry-After} (or
 *   {@code pokeapi.rate-limit.default-pause}). Until then calls fail at once with
 *   {@link PokeApiRateLimitException}, without reaching PokeAPI: the more PokeAPI throttles us, the less we ask.</li>
 * </ul>
 * Transient failures are recognised by {@link RetryableException} (timeouts, connection errors, and the statuses
 * {@link PokeApiErrorDecoder} marks: 429, 502, 503, 504). Anything else, such as a 404, is never retried.
 */
@Component
public class PokeApiRetrier {
    private static final Logger log = LoggerFactory.getLogger(PokeApiRetrier.class);
    private static final int RATE_LIMITED = 429;

    /** Waits the given time; replaced in tests so they do not sleep. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final int maxAttempts;
    private final Duration period;
    private final Duration maxPeriod;
    private final Duration budget;
    private final Duration defaultPause;
    private final Clock clock;
    private final Sleeper sleeper;
    private final AtomicReference<Instant> pausedUntil = new AtomicReference<>(Instant.EPOCH);
    /** End of the budget shared by the calls of the current request, when one is open on this thread. */
    private final ThreadLocal<Instant> requestDeadline = new ThreadLocal<>();

    @Autowired
    public PokeApiRetrier(@Value("${pokeapi.retry.max-attempts:3}") int maxAttempts,
            @Value("${pokeapi.retry.period:100ms}") Duration period,
            @Value("${pokeapi.retry.max-period:1s}") Duration maxPeriod,
            @Value("${pokeapi.retry.budget:6s}") Duration budget,
            @Value("${pokeapi.rate-limit.default-pause:10s}") Duration defaultPause) {
        this(maxAttempts, period, maxPeriod, budget, defaultPause, Clock.systemUTC(),
                duration -> Thread.sleep(duration.toMillis()));
    }

    PokeApiRetrier(int maxAttempts, Duration period, Duration maxPeriod, Duration budget, Duration defaultPause,
            Clock clock, Sleeper sleeper) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.period = period;
        this.maxPeriod = maxPeriod;
        this.budget = budget;
        this.defaultPause = defaultPause;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** A policy that never retries and never pauses: for callers that do not need one (unit tests). */
    public static PokeApiRetrier none() {
        return new PokeApiRetrier(1, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO,
                Clock.systemUTC(), duration -> { });
    }

    /**
     * Runs work that makes several PokeAPI calls for one request under a single time budget: retries stop once the
     * budget opened here is spent, whichever call they belong to. Nested calls join the budget already open.
     */
    public <T> T withinBudget(Supplier<T> work) {
        if (requestDeadline.get() != null) {
            return work.get();
        }
        requestDeadline.set(clock.instant().plus(budget));
        try {
            return work.get();
        } finally {
            requestDeadline.remove();
        }
    }

    /**
     * Runs a PokeAPI call.
     *
     * @param retry whether transient failures may be retried (calls answering a request directly)
     */
    public <T> T execute(Supplier<T> call, boolean retry) {
        Instant start = clock.instant();
        Instant pause = pausedUntil.get();
        if (start.isBefore(pause)) {
            throw new PokeApiRateLimitException(pause, false, null);
        }
        Instant shared = requestDeadline.get();
        Instant deadline = shared != null ? shared : start.plus(budget);
        int attempt = 0;
        while (true) {
            attempt++;
            Instant attemptStart = clock.instant();
            try {
                return call.get();
            } catch (FeignException.NotFound e) {
                // Never retried; the layers above learn "not in PokeAPI" without depending on the HTTP library.
                throw new PokeApiNotFoundException("The requested resource does not exist in PokeAPI", e);
            } catch (RetryableException e) {
                Duration lastAttempt = Duration.between(attemptStart, clock.instant());
                Duration wait;
                if (e.status() == RATE_LIMITED) {
                    Instant until = pause(e.retryAfter());
                    wait = Duration.between(clock.instant(), until);
                    if (!canRetry(retry, attempt, deadline, wait, lastAttempt)) {
                        throw new PokeApiRateLimitException(until, true, e);
                    }
                } else {
                    wait = backoff(attempt);
                    if (!canRetry(retry, attempt, deadline, wait, lastAttempt)) {
                        throw e;
                    }
                }
                log.debug("Retrying PokeAPI call (attempt {} of {}) in {} ms: {}", attempt + 1, maxAttempts,
                        wait.toMillis(), e.getMessage());
                sleep(wait, e);
            }
        }
    }

    /** Instant until which PokeAPI calls are refused locally because of a 429 ({@link Instant#EPOCH} if none). */
    public Instant pausedUntil() {
        return pausedUntil.get();
    }

    /** Retries only if allowed, attempts remain, and the wait plus an attempt like the last one end by the deadline. */
    private boolean canRetry(boolean retry, int attempt, Instant deadline, Duration wait, Duration lastAttempt) {
        if (!retry || attempt >= maxAttempts) {
            return false;
        }
        Instant expectedEnd = clock.instant().plus(wait).plus(lastAttempt);
        return !expectedEnd.isAfter(deadline);
    }

    private Duration backoff(int attempt) {
        long millis = period.toMillis() * (1L << Math.min(attempt - 1, 20));
        return Duration.ofMillis(Math.min(millis, maxPeriod.toMillis()));
    }

    private Instant pause(Long retryAfterEpochMillis) {
        Instant until = retryAfterEpochMillis != null
                ? Instant.ofEpochMilli(retryAfterEpochMillis)
                : clock.instant().plus(defaultPause);
        Instant effective = pausedUntil.accumulateAndGet(until, (current, next) -> next.isAfter(current) ? next : current);
        log.warn("PokeAPI rate limit: pausing calls until {}", effective);
        return effective;
    }

    private void sleep(Duration wait, RetryableException cause) {
        if (wait.isNegative() || wait.isZero()) {
            return;
        }
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }
}
