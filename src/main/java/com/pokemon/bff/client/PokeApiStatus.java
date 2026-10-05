package com.pokemon.bff.client;

import feign.FeignException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Outcome of the latest calls to PokeAPI, recorded from the real traffic, so the health check needs no extra
 * requests to the upstream.
 * <p>
 * A 4xx answer such as a 404 means PokeAPI is reachable and answering, so it counts as a success; only transport
 * errors, timeouts and 5xx count as failures. A 429 is neither: PokeAPI works but is limiting us, which is recorded
 * apart ({@link #rateLimitedUntil()}) so it does not read as an outage.
 */
@Component
public class PokeApiStatus {
    private final Clock clock;
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();
    private final AtomicReference<Instant> lastFailure = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<Instant> rateLimitedUntil = new AtomicReference<>();

    public PokeApiStatus() {
        this(Clock.systemUTC());
    }

    PokeApiStatus(Clock clock) {
        this.clock = clock;
    }

    public void recordSuccess() {
        lastSuccess.set(clock.instant());
    }

    /** Records the exception of a failed call, unless it shows PokeAPI answered normally (e.g. a 404). */
    public void record(RuntimeException exception) {
        if (isUpstreamFailure(exception)) {
            lastFailure.set(clock.instant());
            lastError.set(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        } else {
            recordSuccess();
        }
    }

    /** PokeAPI answered 429: it is reachable, so this also counts as a sign of life. */
    public void recordRateLimit(Instant until) {
        rateLimitedUntil.accumulateAndGet(until, (current, next) -> current == null || next.isAfter(current) ? next : current);
        recordSuccess();
    }

    /** Until when PokeAPI asked us to wait, if that moment is still ahead; {@code null} otherwise. */
    public Instant rateLimitedUntil() {
        Instant until = rateLimitedUntil.get();
        return until != null && until.isAfter(clock.instant()) ? until : null;
    }

    public Instant lastSuccess() {
        return lastSuccess.get();
    }

    public Instant lastFailure() {
        return lastFailure.get();
    }

    public String lastError() {
        return lastError.get();
    }

    static boolean isUpstreamFailure(RuntimeException exception) {
        if (exception instanceof PokeApiRateLimitException || exception instanceof PokeApiNotFoundException) {
            // A rate limit is not an outage, and a 404 means PokeAPI answered.
            return false;
        }
        if (exception instanceof FeignException feign) {
            // RetryableException is a FeignException: status -1 (no response) or a transient 5xx count as failures.
            return feign.status() != 429 && (feign.status() >= 500 || feign.status() < 0);
        }
        // Anything else (e.g. an unreadable body) means the integration is not working.
        return true;
    }
}
