package com.pokemon.bff.exception;

import java.util.NoSuchElementException;

/**
 * The record was deleted through the API (it has a deletion mark), so an update does not bring it back. Answered
 * with 404 like any missing record, and the problem body says {@code "deleted": true} so a client can offer to
 * restore it explicitly ({@code POST /{id}/sync}) instead of parsing the message.
 */
public class PokemonDeletedException extends NoSuchElementException {

    public PokemonDeletedException(String message) {
        super(message);
    }
}
