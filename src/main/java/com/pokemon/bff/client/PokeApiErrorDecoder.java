package com.pokemon.bff.client;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Marks transient PokeAPI failures as retryable so the Feign {@code Retryer} tries them again.
 * <p>
 * Only {@code GET} requests are retried (they are idempotent), and only for statuses that usually clear up on
 * their own: 429 (rate limit) and 502/503/504. Any other status keeps Feign's regular exception, so a 404 is never
 * retried. The status stays on the exception, which lets the API tell a rate limit (503) from an upstream failure
 * (502) once the retries are exhausted.
 */
public class PokeApiErrorDecoder implements ErrorDecoder {
    static final Set<Integer> TRANSIENT_STATUSES = Set.of(429, 502, 503, 504);
    private static final String RETRY_AFTER = "Retry-After";

    @Override
    public Exception decode(String methodKey, Response response) {
        FeignException exception = FeignException.errorStatus(methodKey, response);
        Request request = response.request();
        boolean idempotent = request != null && request.httpMethod() == Request.HttpMethod.GET;
        if (idempotent && TRANSIENT_STATUSES.contains(response.status())) {
            return new RetryableException(response.status(), exception.getMessage(), request.httpMethod(),
                    exception, retryAfter(response), request);
        }
        return exception;
    }

    /** {@code Retry-After} in seconds as an epoch-millis instant, or {@code null} to let the Retryer decide. */
    private static Long retryAfter(Response response) {
        Collection<String> values = response.headers() == null ? null : response.headers().entrySet().stream()
                .filter(header -> RETRY_AFTER.equalsIgnoreCase(header.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(values.iterator().next().trim());
            return System.currentTimeMillis() + Math.max(0, seconds) * 1000;
        } catch (NumberFormatException e) {
            // HTTP-date form: not worth parsing here, the Retryer's own backoff applies.
            return null;
        }
    }
}
