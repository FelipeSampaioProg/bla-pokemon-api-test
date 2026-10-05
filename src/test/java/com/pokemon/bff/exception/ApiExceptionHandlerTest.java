package com.pokemon.bff.exception;

import com.pokemon.bff.client.PokeApiNotFoundException;
import com.pokemon.bff.client.PokeApiRateLimitException;
import com.pokemon.bff.dto.PokemonUpdateRequest;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.context.request.ServletWebRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests of the mapping from exceptions to problem bodies (RFC 9457). The HTTP tests check the same mapping
 * through Spring MVC; these reach the branches a request rarely does (each upstream failure, each expected type).
 */
class ApiExceptionHandlerTest {
    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private static final Request REQUEST = Request.create(Request.HttpMethod.GET, "https://pokeapi.co/api/v2/x",
            Map.of(), null, StandardCharsets.UTF_8, null);

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    /** The problem body Spring MVC would send for a request body that Jackson could not read. */
    private ProblemDetail unreadable(String json) {
        JacksonException cause = assertThrows(JacksonException.class,
                () -> STRICT.readValue(json, PokemonUpdateRequest.class));
        var exception = new HttpMessageNotReadableException("unreadable", cause, new MockHttpInputMessage(new byte[0]));
        var response = handler.handleHttpMessageNotReadable(exception, new HttpHeaders(), HttpStatus.BAD_REQUEST,
                new ServletWebRequest(new MockHttpServletRequest()));
        assertNotNull(response);
        var problem = (ProblemDetail) response.getBody();
        assertEquals(400, problem.getStatus());
        assertEquals("Invalid request", problem.getTitle());
        return problem;
    }

    private static Object field(ProblemDetail problem) {
        return problem.getProperties() == null ? null : problem.getProperties().get("field");
    }

    @Test
    void shouldNameTheFieldAndTheExpectedTypeOfAnUnreadableBody() {
        assertEquals("Missing or invalid value for 'stats[0].value': expected an integer",
                unreadable("{\"stats\":[{\"name\":\"hp\",\"value\":\"x\"}]}").getDetail());
        assertEquals("Missing or invalid value for 'height': expected a number",
                unreadable("{\"height\":\"tall\"}").getDetail());
        assertEquals("Missing or invalid value for 'name': expected a text",
                unreadable("{\"name\":[1]}").getDetail());
        assertEquals("Missing or invalid value for 'stats': expected a list",
                unreadable("{\"stats\":{\"name\":\"hp\"}}").getDetail());
        var notAnObject = unreadable("{\"stats\":[5]}");
        assertEquals("stats[0]", field(notAnObject));
        assertEquals("Missing or invalid value for 'stats[0]': expected an object", notAnObject.getDetail());
    }

    @Test
    void shouldListTheAcceptedFieldsForAnUnknownOne() {
        var problem = unreadable("{\"heigth\":5}");

        assertEquals("heigth", field(problem));
        assertTrue(problem.getDetail().startsWith("Unknown field 'heigth'; the accepted fields are "));
        assertTrue(problem.getDetail().contains("height"));
    }

    @Test
    void shouldExplainMalformedJsonAndNonObjectBodies() {
        assertTrue(unreadable("{\"name\":").getDetail().startsWith("Malformed JSON: "));
        assertTrue(unreadable("{\"name\":\"a\",\"name\":\"b\"}").getDetail().contains("Duplicate"));
        var array = unreadable("[]");
        assertEquals("The request body must be a JSON object", array.getDetail());
        assertNull(field(array));
    }

    @Test
    void shouldKeepTheGenericMessageWhenTheCauseIsNotJson() {
        var exception = new HttpMessageNotReadableException("no body", new IllegalStateException("x"),
                new MockHttpInputMessage(new byte[0]));
        var response = handler.handleHttpMessageNotReadable(exception, new HttpHeaders(), HttpStatus.BAD_REQUEST,
                new ServletWebRequest(new MockHttpServletRequest()));

        assertEquals("Malformed request body", ((ProblemDetail) response.getBody()).getDetail());
    }

    @Test
    void shouldGiveSpringMvcErrorsTheSameTitleAndNameTheParameter() {
        var mismatch = new TypeMismatchException("abc", Integer.class);
        mismatch.initPropertyName("id");
        var body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Failed to convert 'id'");

        handler.handleExceptionInternal(mismatch, body, new HttpHeaders(), HttpStatus.BAD_REQUEST,
                new ServletWebRequest(new MockHttpServletRequest()));

        assertEquals("Invalid request", body.getTitle());
        assertEquals("id", field(body));
        assertEquals("Invalid value for 'id': expected an integer", body.getDetail());
    }

    @Test
    void shouldDescribeEveryExpectedType() {
        for (var expected : Map.of(Boolean.class, "true or false", String[].class, "a list", Object.class,
                "an object").entrySet()) {
            var mismatch = new TypeMismatchException("x", expected.getKey());
            mismatch.initPropertyName("p");
            var body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            handler.handleExceptionInternal(mismatch, body, new HttpHeaders(), HttpStatus.BAD_REQUEST,
                    new ServletWebRequest(new MockHttpServletRequest()));
            assertEquals("Invalid value for 'p': expected " + expected.getValue(), body.getDetail());
        }
        var unknownType = new TypeMismatchException("x", null);
        unknownType.initPropertyName("p");
        var body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        handler.handleExceptionInternal(unknownType, body, new HttpHeaders(), HttpStatus.BAD_REQUEST,
                new ServletWebRequest(new MockHttpServletRequest()));
        assertEquals("Invalid value for 'p': expected a value of another type", body.getDetail());
    }

    @Test
    void shouldLeaveOtherStatusesAsSpringBuildsThem() {
        var body = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        body.setTitle("Method Not Allowed");

        handler.handleExceptionInternal(new IllegalStateException(), body, new HttpHeaders(),
                HttpStatus.METHOD_NOT_ALLOWED, new ServletWebRequest(new MockHttpServletRequest()));

        assertEquals("Method Not Allowed", body.getTitle());
    }

    @Test
    void shouldMapTheDomainErrors() {
        var invalid = handler.handleInvalidField(new InvalidFieldException("stats[0].value", "Stat values must be between 0 and 255"));
        assertEquals("stats[0].value", field(invalid));
        assertEquals("Invalid request", handler.handleInvalidRequest(new IllegalArgumentException("bad")).getTitle());

        var missing = handler.handleNotFound(new java.util.NoSuchElementException("Pokemon not found with id 9"));
        assertEquals(404, missing.getStatus());
        assertEquals("Resource not found", missing.getTitle());
        var deleted = handler.handleDeleted(new PokemonDeletedException("was deleted"));
        assertEquals(Boolean.TRUE, deleted.getProperties().get("deleted"));
        var notInPokeApi = handler.handleUpstreamNotFound(new PokeApiNotFoundException("x", null));
        assertEquals("The requested Pokemon does not exist in PokeAPI", notInPokeApi.getDetail());

        assertEquals("Concurrent modification", handler.handleConcurrentModification(
                new ObjectOptimisticLockingFailureException("PokemonEntity", 25)).getTitle());
        var conflict = handler.handleDataConflict(new DataIntegrityViolationException("Duplicate entry 'pikachu'"));
        assertEquals(409, conflict.getStatus());
        assertFalse(conflict.getDetail().contains("Duplicate entry"), "the database message is logged, not returned");
    }

    @Test
    void shouldSendTheCurrentVersionAsETagOnlyWhenTheRecordExists() {
        var changed = handler.handlePreconditionFailed(new PreconditionFailedException("changed", "4-100"));
        assertEquals(412, changed.getStatusCode().value());
        assertEquals("\"4-100\"", changed.getHeaders().getETag());

        var absent = handler.handlePreconditionFailed(new PreconditionFailedException("not stored yet", null));
        assertNull(absent.getHeaders().getETag());
    }

    @Test
    void shouldTellTheUpstreamFailuresApart() {
        var rateLimited = handler.handleUpstreamRetriesExhausted(
                new RetryableException(429, "429", Request.HttpMethod.GET, null, (Long) null, REQUEST));
        assertEquals(503, rateLimited.getStatusCode().value());
        assertEquals(String.valueOf(ApiExceptionHandler.DEFAULT_RETRY_AFTER_SECONDS),
                rateLimited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));

        var failing = handler.handleUpstreamRetriesExhausted(
                new RetryableException(503, "503", Request.HttpMethod.GET, null, (Long) null, REQUEST));
        assertEquals(502, failing.getStatusCode().value());

        var silent = handler.handleUpstreamRetriesExhausted(
                new RetryableException(-1, "timeout", Request.HttpMethod.GET, null, (Long) null, REQUEST));
        assertEquals(504, silent.getStatusCode().value());
    }

    @Test
    void shouldAskClientsToWaitUntilARateLimitLifts() {
        var paused = handler.handleRateLimitPause(new PokeApiRateLimitException(Instant.now().plusSeconds(20), true, null));
        long seconds = Long.parseLong(paused.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertTrue(seconds >= 19 && seconds <= 20, "was " + seconds);

        var lifted = handler.handleRateLimitPause(new PokeApiRateLimitException(Instant.now().minusSeconds(5), true, null));
        assertEquals("1", lifted.getHeaders().getFirst(HttpHeaders.RETRY_AFTER), "never less than one second");

        var tooMany = (FeignException.TooManyRequests) FeignException.errorStatus("x",
                Response.builder().status(429).reason("x").request(REQUEST).headers(Map.of()).build());
        assertEquals(503, handler.handleUpstreamRateLimit(tooMany).getStatusCode().value());
    }

    @Test
    void shouldHideUnexpectedAndUpstreamDetails() {
        var upstream = handler.handleUpstreamFailure(FeignException.errorStatus("x",
                Response.builder().status(500).reason("secret").request(REQUEST).headers(Map.of()).build()));
        assertEquals(502, upstream.getStatus());
        assertFalse(upstream.getDetail().contains("secret"));

        var unexpected = handler.handleUnexpected(new IllegalStateException("jdbc:mysql://user:password@db"));
        assertEquals(500, unexpected.getStatus());
        assertEquals("An unexpected error occurred", unexpected.getDetail());
    }
}
