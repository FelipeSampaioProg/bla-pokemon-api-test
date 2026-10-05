package com.pokemon.bff.exception;

import com.pokemon.bff.client.PokeApiNotFoundException;
import com.pokemon.bff.client.PokeApiRateLimitException;
import com.pokemon.bff.controller.EntityTags;
import feign.FeignException;
import feign.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.TreeSet;

/**
 * Translates exceptions into RFC 9457 {@link ProblemDetail} bodies so every error response shares one structure.
 * Spring MVC's own errors (type mismatches, unreadable bodies, ...) are handled by the parent class in the same format.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    /** The title of every 400, whether the request failed in Spring MVC, in the JSON or in the business rules. */
    static final String INVALID_REQUEST = "Invalid request";
    static final String NOT_FOUND = "Resource not found";
    static final long DEFAULT_RETRY_AFTER_SECONDS = 30;

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidFieldException.class)
    public ProblemDetail handleInvalidField(InvalidFieldException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, e.getMessage());
        problem.setProperty("field", e.getField());
        return problem;
    }

    /** The record changed since the client read it; the current ETag lets it see that its copy is stale. */
    @ExceptionHandler(PreconditionFailedException.class)
    public ResponseEntity<ProblemDetail> handlePreconditionFailed(PreconditionFailedException e) {
        var response = ResponseEntity.status(HttpStatus.PRECONDITION_FAILED);
        if (e.getCurrentVersion() != null) {
            response.eTag(EntityTags.format(e.getCurrentVersion()));
        }
        return response.body(problem(HttpStatus.PRECONDITION_FAILED, "Precondition failed", e.getMessage()));
    }

    /**
     * A body that cannot be read as the expected JSON. Spring's default detail ("Failed to read request") does not
     * say what is wrong; this one names the field and the problem, without echoing internals (class names, source
     * positions).
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, "Malformed request body");
        JacksonException jackson = findCause(ex, JacksonException.class);
        if (jackson != null) {
            describe(jackson, problem);
        }
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Errors Spring MVC answers itself (an id that is not a number, a missing parameter...) get the same title as
     * every other 400, and a parameter of the wrong type names its field like the body errors do.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem && status.value() == HttpStatus.BAD_REQUEST.value()) {
            problem.setTitle(INVALID_REQUEST);
            if (ex instanceof TypeMismatchException mismatch && mismatch.getPropertyName() != null) {
                problem.setProperty("field", mismatch.getPropertyName());
                problem.setDetail("Invalid value for '" + mismatch.getPropertyName() + "': expected "
                        + describe(mismatch.getRequiredType()));
            }
        }
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }

    private static void describe(JacksonException e, ProblemDetail problem) {
        String field = fieldPath(e);
        if (!field.isEmpty()) {
            problem.setProperty("field", field);
        }
        if (e instanceof UnrecognizedPropertyException unknown) {
            var known = new TreeSet<String>();
            unknown.getKnownPropertyIds().forEach(id -> known.add(String.valueOf(id)));
            problem.setDetail("Unknown field '" + field + "'; the accepted fields are " + String.join(", ", known));
        } else if (e instanceof MismatchedInputException mismatch) {
            problem.setDetail(field.isEmpty()
                    ? "The request body must be a JSON object"
                    : "Missing or invalid value for '" + field + "': expected " + describe(mismatch.getTargetType()));
        } else if (e instanceof StreamReadException) {
            problem.setDetail("Malformed JSON: " + e.getOriginalMessage());
        }
    }

    /** {@code stats[1].value}, from Jackson's path of property names and array indexes. */
    private static String fieldPath(JacksonException e) {
        var path = new StringBuilder();
        for (JacksonException.Reference reference : e.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String describe(Class<?> type) {
        if (type == null) {
            return "a value of another type";
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return "an integer";
        }
        if (Number.class.isAssignableFrom(type) || type == double.class || type == float.class) {
            return "a number";
        }
        if (type == String.class) {
            return "a text";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "true or false";
        }
        if (Collection.class.isAssignableFrom(type) || type.isArray()) {
            return "a list";
        }
        return "an object";
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException e) {
        return problem(HttpStatus.NOT_FOUND, NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PokemonDeletedException.class)
    public ProblemDetail handleDeleted(PokemonDeletedException e) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, NOT_FOUND, e.getMessage());
        problem.setProperty("deleted", true);
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentModification(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The Pokemon was modified by another operation, please retry");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataConflict(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The Pokemon conflicts with an existing record (duplicate id or name)");
    }

    @ExceptionHandler(PokeApiNotFoundException.class)
    public ProblemDetail handleUpstreamNotFound(PokeApiNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, NOT_FOUND, "The requested Pokemon does not exist in PokeAPI");
    }

    /**
     * What is left once the retries are exhausted. The upstream status tells the cases apart: a rate limit becomes
     * 503 with {@code Retry-After}, an upstream error 502, and no response at all (status -1: timeout, connection
     * refused) 504.
     */
    @ExceptionHandler(RetryableException.class)
    public ResponseEntity<ProblemDetail> handleUpstreamRetriesExhausted(RetryableException e) {
        if (e.status() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return rateLimited(e.retryAfter());
        }
        if (e.status() >= 500) {
            log.warn("PokeAPI kept failing with status {}: {}", e.status(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(problem(HttpStatus.BAD_GATEWAY, "Upstream failure", "PokeAPI request failed, please retry later"));
        }
        log.warn("PokeAPI did not respond: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(problem(HttpStatus.GATEWAY_TIMEOUT, "Upstream timeout", "PokeAPI did not respond in time, please retry later"));
    }

    @ExceptionHandler(PokeApiRateLimitException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitPause(PokeApiRateLimitException e) {
        return rateLimited(e.retryAfter().toEpochMilli());
    }

    @ExceptionHandler(FeignException.TooManyRequests.class)
    public ResponseEntity<ProblemDetail> handleUpstreamRateLimit(FeignException.TooManyRequests e) {
        return rateLimited(null);
    }

    /** PokeAPI is throttling us: our service is temporarily unavailable, and the client should wait. */
    private ResponseEntity<ProblemDetail> rateLimited(Long retryAfterEpochMillis) {
        long seconds = retryAfterEpochMillis == null ? DEFAULT_RETRY_AFTER_SECONDS
                : Math.max(1, (retryAfterEpochMillis - System.currentTimeMillis() + 999) / 1000);
        log.warn("PokeAPI rate limit reached; asking clients to retry after {} s", seconds);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "Upstream rate limit",
                        "PokeAPI is limiting requests, please retry later"));
    }

    @ExceptionHandler(FeignException.class)
    public ProblemDetail handleUpstreamFailure(FeignException e) {
        log.warn("PokeAPI request failed with status {}: {}", e.status(), e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "Upstream failure", "PokeAPI request failed, please retry later");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unexpected error while handling request", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
