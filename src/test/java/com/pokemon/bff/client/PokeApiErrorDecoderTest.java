package com.pokemon.bff.client;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PokeApiErrorDecoderTest {
    private final PokeApiErrorDecoder decoder = new PokeApiErrorDecoder();

    private static Response response(Request.HttpMethod method, int status, Map<String, Collection<String>> headers) {
        var request = Request.create(method, "https://pokeapi.co/api/v2/pokemon/pikachu", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return Response.builder().status(status).reason("upstream").request(request).headers(headers).build();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 502, 503, 504})
    void shouldMakeTransientFailuresOfGetRetryable(int status) {
        var exception = decoder.decode("PokemonClient#findByNameOrId", response(Request.HttpMethod.GET, status, Map.of()));

        var retryable = assertInstanceOf(RetryableException.class, exception);
        assertEquals(status, retryable.status());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 500})
    void shouldNotRetryClientErrorsNorGenericServerErrors(int status) {
        var exception = decoder.decode("PokemonClient#findByNameOrId", response(Request.HttpMethod.GET, status, Map.of()));

        assertFalse(exception instanceof RetryableException);
        assertEquals(status, ((FeignException) exception).status());
    }

    @Test
    void shouldNotRetryNonIdempotentRequests() {
        var exception = decoder.decode("PokemonClient#create", response(Request.HttpMethod.POST, 503, Map.of()));

        assertFalse(exception instanceof RetryableException);
    }

    @Test
    void shouldCarryRetryAfterSecondsAsInstant() {
        long before = System.currentTimeMillis();
        var exception = (RetryableException) decoder.decode("PokemonClient#findAll",
                response(Request.HttpMethod.GET, 429, Map.of("Retry-After", List.of("7"))));

        assertTrue(exception.retryAfter() >= before + 7_000);
        assertTrue(exception.retryAfter() <= System.currentTimeMillis() + 7_000);
    }

    @Test
    void shouldIgnoreRetryAfterThatIsNotInSeconds() {
        var exception = (RetryableException) decoder.decode("PokemonClient#findAll",
                response(Request.HttpMethod.GET, 503, Map.of("Retry-After", List.of("Wed, 21 Oct 2026 07:28:00 GMT"))));

        assertNull(exception.retryAfter());
    }
}
