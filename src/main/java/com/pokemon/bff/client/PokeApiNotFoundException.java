package com.pokemon.bff.client;

/**
 * PokeAPI answered that the requested resource does not exist (404). Thrown by the client layer instead of the HTTP
 * library's own exception, so the business layer can act on "not in PokeAPI" without knowing how PokeAPI is called.
 */
public class PokeApiNotFoundException extends RuntimeException {

    public PokeApiNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
