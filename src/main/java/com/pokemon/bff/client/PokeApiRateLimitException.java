package com.pokemon.bff.client;

import java.time.Instant;

/**
 * PokeAPI is rate limiting us and no call should be made before {@link #retryAfter()}.
 *
 * @see PokeApiRetrier
 */
public class PokeApiRateLimitException extends RuntimeException {
    private final Instant retryAfter;
    private final boolean upstreamCalled;

    /**
     * @param upstreamCalled {@code true} when PokeAPI itself answered 429; {@code false} when the call was refused
     *                       locally because a previous 429 is still in force (no request was sent).
     */
    public PokeApiRateLimitException(Instant retryAfter, boolean upstreamCalled, Throwable cause) {
        super("PokeAPI rate limit in force until " + retryAfter, cause);
        this.retryAfter = retryAfter;
        this.upstreamCalled = upstreamCalled;
    }

    public Instant retryAfter() {
        return retryAfter;
    }

    public boolean upstreamCalled() {
        return upstreamCalled;
    }
}
